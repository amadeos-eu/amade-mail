package org.datazuul.amadeos.mail.ui.common;

import com.google.inject.Inject;
import org.datazuul.amadeos.mail.domain.Account;
import org.datazuul.amadeos.mail.repository.PasswordStore;
import org.datazuul.amadeos.mail.service.CredentialProvider;
import org.datazuul.amadeos.mail.service.MailServiceException;

import javax.swing.*;
import java.awt.*;
import java.lang.reflect.InvocationTargetException;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Takes passwords from the keyring and asks the user (on the EDT) when none is stored or the
 * server rejected the stored one.
 */
public final class PromptingCredentialProvider implements CredentialProvider {
    private final PasswordStore store;
    private final Set<String> rejected = ConcurrentHashMap.newKeySet();
    /** Passwords entered without "Remember", kept until the mailer exits. */
    private final java.util.Map<String, String> session = new ConcurrentHashMap<>();

    @Inject
    public PromptingCredentialProvider(PasswordStore store) {
        this.store = store;
    }

    @Override
    public synchronized String password(Account account) {
        if (!rejected.contains(account.id())) {
            String known = session.get(account.id());
            if (known != null) return known;
            Optional<String> stored = store.lookup(account.id());
            if (stored.isPresent()) return stored.get();
        }
        Prompt prompt = ask(account);
        if (prompt == null) throw new MailServiceException(account.label() + ": no password entered.");
        rejected.remove(account.id());
        session.put(account.id(), prompt.password());
        if (prompt.remember()) store.store(account.id(), "amade-mail " + account.email(), prompt.password());
        return prompt.password();
    }

    @Override
    public void rejected(Account account) {
        rejected.add(account.id());
        session.remove(account.id());
    }

    @Override
    public void passwordChanged(String accountId, String password) {
        rejected.remove(accountId);
        session.put(accountId, password);
    }

    private record Prompt(String password, boolean remember) {
    }

    private Prompt ask(Account account) {
        AtomicReference<Prompt> result = new AtomicReference<>();
        Runnable dialog = () -> {
            JPasswordField field = new JPasswordField(24);
            JCheckBox remember = new JCheckBox("Remember password", true);
            remember.setOpaque(false);
            if (!store.isPersistent()) {
                remember.setSelected(false);
                remember.setEnabled(false);
                remember.setToolTipText("No keyring available (install libsecret-tools)");
            }
            JPanel panel = new JPanel(new GridLayout(0, 1, 0, 4));
            panel.setOpaque(false);
            String reason = rejected.contains(account.id()) ? "The server rejected the password for " : "Password for ";
            panel.add(new JLabel(reason + account.username() + " on " + account.imap().host() + ":"));
            panel.add(field);
            panel.add(remember);
            JOptionPane pane = new JOptionPane(panel, JOptionPane.QUESTION_MESSAGE, JOptionPane.OK_CANCEL_OPTION);
            JDialog d = pane.createDialog(null, "Mailer - " + account.label());
            d.addWindowFocusListener(new java.awt.event.WindowAdapter() {
                @Override
                public void windowGainedFocus(java.awt.event.WindowEvent e) {
                    field.requestFocusInWindow();
                }
            });
            d.setVisible(true);
            d.dispose();
            if (Integer.valueOf(JOptionPane.OK_OPTION).equals(pane.getValue()) && field.getPassword().length > 0) {
                result.set(new Prompt(new String(field.getPassword()), remember.isSelected()));
            }
        };
        if (SwingUtilities.isEventDispatchThread()) {
            dialog.run();
        } else {
            try {
                SwingUtilities.invokeAndWait(dialog);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            } catch (InvocationTargetException e) {
                throw new MailServiceException("Password dialog failed", e.getCause());
            }
        }
        return result.get();
    }
}
