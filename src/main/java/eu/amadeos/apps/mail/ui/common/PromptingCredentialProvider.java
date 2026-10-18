package eu.amadeos.apps.mail.ui.common;

import static eu.amadeos.apps.mail.i18n.Texts.tr;

import com.google.inject.Inject;
import eu.amadeos.commons.desktop.DesktopLauncher;
import eu.amadeos.apps.mail.domain.Account;
import eu.amadeos.commons.desktop.PasswordStore;
import eu.amadeos.apps.mail.domain.OAuth2Client;
import eu.amadeos.apps.mail.domain.OAuth2Provider;
import eu.amadeos.apps.mail.repository.OAuth2ClientRepository;
import eu.amadeos.apps.mail.service.CredentialProvider;
import eu.amadeos.apps.mail.service.MailServiceException;
import eu.amadeos.apps.mail.service.OAuth2Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Takes passwords from the keyring and asks the user (on the EDT) when none is stored or the
 * server rejected the stored one. For OAuth2 accounts it supplies access tokens and lets the user
 * sign in in the web browser when no valid refresh token is left.
 */
public final class PromptingCredentialProvider implements CredentialProvider {
    private static final Logger LOG = LoggerFactory.getLogger(PromptingCredentialProvider.class);
    /** After this many rejected access tokens in a row the user signs in again. */
    private static final int MAX_TOKEN_REJECTIONS = 2;

    private final PasswordStore store;
    private final OAuth2Service oauth;
    private final OAuth2ClientRepository clients;
    private final DesktopLauncher desktop;
    private final Set<String> rejected = ConcurrentHashMap.newKeySet();
    /** Passwords entered without "Remember", kept until the mailer exits. */
    private final Map<String, String> session = new ConcurrentHashMap<>();
    private final Map<String, Integer> tokenRejections = new ConcurrentHashMap<>();

    @Inject
    public PromptingCredentialProvider(PasswordStore store, OAuth2Service oauth, OAuth2ClientRepository clients,
                                       DesktopLauncher desktop) {
        this.store = store;
        this.oauth = oauth;
        this.clients = clients;
        this.desktop = desktop;
    }

    @Override
    public synchronized String password(Account account) {
        if (account.auth().isOAuth2()) return accessToken(account);
        if (!rejected.contains(account.id())) {
            String known = session.get(account.id());
            if (known != null) return known;
            Optional<String> stored = store.lookup(account.id());
            if (stored.isPresent()) return stored.get();
        }
        Prompt prompt = ask(account);
        if (prompt == null) throw new MailServiceException(tr("credentials.no-password", account.label()));
        rejected.remove(account.id());
        session.put(account.id(), prompt.password());
        if (prompt.remember()) store.store(account.id(), "amade-mail " + account.email(), prompt.password());
        return prompt.password();
    }

    @Override
    public void rejected(Account account) {
        if (account.auth().isOAuth2()) {
            oauth.invalidate(account.id());
            tokenRejections.merge(account.id(), 1, Integer::sum);
            return;
        }
        rejected.add(account.id());
        session.remove(account.id());
    }

    @Override
    public void passwordChanged(String accountId, String password) {
        rejected.remove(accountId);
        session.put(accountId, password);
    }

    @Override
    public void signOut(String accountId) {
        oauth.forget(accountId);
        tokenRejections.remove(accountId);
    }

    // ---- OAuth2 -----------------------------------------------------------------------------------

    private String accessToken(Account account) {
        OAuth2Provider provider = account.auth().provider();
        OAuth2Client client = clients.load(provider).orElse(null);
        if (client == null) {
            client = onEdt(() -> askClient(provider));
            if (client == null) throw new MailServiceException(tr("credentials.no-client", account.label()));
            clients.save(provider, client);
        }
        if (tokenRejections.getOrDefault(account.id(), 0) < MAX_TOKEN_REJECTIONS) {
            Optional<String> token = oauth.accessToken(account, client);
            if (token.isPresent()) return token.get();
        }
        String token = signIn(account, client);
        tokenRejections.remove(account.id());
        return token;
    }

    private String signIn(Account account, OAuth2Client client) {
        CompletableFuture<Void> cancelled = new CompletableFuture<>();
        AtomicReference<JDialog> waiting = new AtomicReference<>();
        try {
            return oauth.signIn(account, client, uri -> {
                browse(uri);
                SwingUtilities.invokeLater(() -> waiting.set(showWaiting(account, uri, cancelled)));
            }, cancelled);
        } finally {
            // Queued after the invokeLater above, so the dialog exists by then.
            SwingUtilities.invokeLater(() -> {
                if (waiting.get() != null) waiting.get().dispose();
            });
        }
    }

    private void browse(URI uri) {
        try {
            desktop.browse(uri.toString());
        } catch (IOException e) {
            LOG.warn("Cannot open the web browser for the sign-in: {}", e.getMessage());
        }
    }

    /** Non-modal, so the user can work in the browser; closing it cancels the sign-in. */
    private JDialog showWaiting(Account account, URI uri, CompletableFuture<Void> cancelled) {
        JDialog dialog = new JDialog((Frame) null, tr("app.title.with", account.label()), false);
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        panel.add(new JLabel("<html>" + tr("credentials.sign-in", "<b>" + account.username() + "</b>",
                account.auth().provider().label()) + "<br>" + tr("credentials.copy-address") + "</html>"),
                BorderLayout.NORTH);
        JTextField address = new JTextField(uri.toString(), 40);
        address.setEditable(false);
        address.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                address.selectAll();
            }
        });
        panel.add(address, BorderLayout.CENTER);
        JButton again = new JButton(tr("credentials.open-again"));
        again.addActionListener(e -> browse(uri));
        JButton cancel = new JButton(UIManager.getString("OptionPane.cancelButtonText"));
        cancel.addActionListener(e -> {
            cancelled.complete(null);
            dialog.dispose();
        });
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 0));
        buttons.setOpaque(false);
        buttons.add(again);
        buttons.add(cancel);
        panel.add(buttons, BorderLayout.SOUTH);
        dialog.setContentPane(panel);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        dialog.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                cancelled.complete(null);
            }
        });
        dialog.pack();
        dialog.setLocationRelativeTo(null);
        dialog.setVisible(true);
        return dialog;
    }

    private static OAuth2Client askClient(OAuth2Provider provider) {
        JTextField clientId = new JTextField(36);
        JTextField clientSecret = new JTextField(36);
        JPanel panel = new JPanel(new GridLayout(0, 1, 0, 4));
        panel.setOpaque(false);
        panel.add(new JLabel("<html>" + tr("credentials.client.needed", provider.label()) + "<br>"
                + provider.consoleHint() + ".</html>"));
        panel.add(new JLabel(tr("credentials.client.id")));
        panel.add(clientId);
        panel.add(new JLabel(tr("credentials.client.secret")));
        panel.add(clientSecret);
        int answer = JOptionPane.showConfirmDialog(null, panel, tr("app.title.with", tr("credentials.client.title", provider.label())),
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (answer != JOptionPane.OK_OPTION) return null;
        OAuth2Client client = new OAuth2Client(clientId.getText(), clientSecret.getText());
        return client.isComplete() ? client : null;
    }

    // ---- password ---------------------------------------------------------------------------------

    private record Prompt(String password, boolean remember) {
    }

    private Prompt ask(Account account) {
        return onEdt(() -> {
            JPasswordField field = new JPasswordField(24);
            JCheckBox remember = new JCheckBox(tr("credentials.remember"), true);
            remember.setOpaque(false);
            if (!store.isPersistent()) {
                remember.setSelected(false);
                remember.setEnabled(false);
                remember.setToolTipText(tr("credentials.no-keyring"));
            }
            JPanel panel = new JPanel(new GridLayout(0, 1, 0, 4));
            panel.setOpaque(false);
            panel.add(new JLabel(tr(rejected.contains(account.id()) ? "credentials.rejected" : "credentials.password",
                    account.username(), account.imap().host())));
            panel.add(field);
            panel.add(remember);
            JOptionPane pane = new JOptionPane(panel, JOptionPane.QUESTION_MESSAGE, JOptionPane.OK_CANCEL_OPTION);
            JDialog d = pane.createDialog(null, tr("app.title.with", account.label()));
            d.addWindowFocusListener(new WindowAdapter() {
                @Override
                public void windowGainedFocus(WindowEvent e) {
                    field.requestFocusInWindow();
                }
            });
            d.setVisible(true);
            d.dispose();
            if (Integer.valueOf(JOptionPane.OK_OPTION).equals(pane.getValue()) && field.getPassword().length > 0) {
                return new Prompt(new String(field.getPassword()), remember.isSelected());
            }
            return null;
        });
    }

    /** Runs a dialog on the EDT and waits for its result; null if interrupted. */
    private static <T> T onEdt(Supplier<T> dialog) {
        if (SwingUtilities.isEventDispatchThread()) return dialog.get();
        AtomicReference<T> result = new AtomicReference<>();
        try {
            SwingUtilities.invokeAndWait(() -> result.set(dialog.get()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (InvocationTargetException e) {
            throw new MailServiceException("Dialog failed", e.getCause());
        }
        return result.get();
    }
}
