package eu.amadeos.apps.mail.ui.options;

import static eu.amadeos.apps.mail.i18n.Texts.tr;

import eu.amadeos.apps.mail.domain.Account;
import eu.amadeos.apps.mail.domain.MailPreferences;
import eu.amadeos.commons.i18n.LanguageChooser;
import eu.amadeos.commons.i18n.LocaleSettings;
import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.commons.swing.AmadeUI;
import eu.amadeos.apps.mail.i18n.Texts;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Mail Options, as in dtmail: a single dialog whose page is chosen with the "Category" menu. Pages are
 * named by id and shown translated ({@code options.page.<id>}).
 */
public final class OptionsDialog extends JDialog {
    public static final String ACCOUNTS = "Accounts";
    public static final String MESSAGE_VIEW = "Message View";
    public static final String LANGUAGE = "Language";

    /** Receives the settings when the user presses OK or Apply. */
    public interface ApplyHandler {
        void apply(List<Account> accounts, Map<String, String> passwords, MailPreferences preferences);
    }

    private final AccountsPanel accounts;
    private final MessageViewOptionsPanel view = new MessageViewOptionsPanel();
    private final JComboBox<String> category = new JComboBox<>(new String[]{ACCOUNTS, MESSAGE_VIEW, LANGUAGE});
    private final CardLayout cards = new CardLayout();
    private final JPanel pages = new JPanel(cards);
    private final LocaleSettings locales;
    private final LanguageChooser language;

    /** @param locales the Mailer's own language setting, shown on the Language page */
    public OptionsDialog(Frame owner, AccountsPanel.ConnectionTester tester, ApplyHandler handler,
                         LocaleSettings locales) {
        super(owner, tr("options.title"), true);
        this.locales = locales;
        language = new LanguageChooser(locales, Messages.commons(Texts.messages().locale()));
        category.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected,
                                                          boolean focus) {
                return super.getListCellRendererComponent(list, value == null ? "" : tr("options.page." + value),
                        index, selected, focus);
            }
        });
        accounts = new AccountsPanel(tester);
        JPanel content = new JPanel(new BorderLayout(0, 10));
        content.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        top.setOpaque(false);
        top.add(new JLabel(tr("options.category")));
        top.add(category);
        category.addActionListener(e -> cards.show(pages, (String) category.getSelectedItem()));
        content.add(top, BorderLayout.NORTH);

        pages.setOpaque(false);
        pages.add(accounts, ACCOUNTS);
        pages.add(view, MESSAGE_VIEW);
        pages.add(languagePage(), LANGUAGE);
        content.add(pages, BorderLayout.CENTER);

        JButton ok = new JButton(UIManager.getString("OptionPane.okButtonText"));
        JButton apply = new JButton(tr("options.apply"));
        JButton cancel = new JButton(UIManager.getString("OptionPane.cancelButtonText"));
        ok.addActionListener(e -> {
            handler.apply(accounts.accounts(), accounts.passwords(), view.preferences());
            applyLanguage();
            dispose();
        });
        apply.addActionListener(e -> {
            handler.apply(accounts.accounts(), accounts.passwords(), view.preferences());
            applyLanguage();
        });
        cancel.addActionListener(e -> dispose());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 0));
        buttons.setOpaque(false);
        buttons.add(ok);
        buttons.add(apply);
        buttons.add(cancel);
        content.add(buttons, BorderLayout.SOUTH);

        setContentPane(content);
        getRootPane().setDefaultButton(ok);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
    }

    private JPanel languagePage() {
        JPanel page = new JPanel(new GridBagLayout());
        page.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.anchor = GridBagConstraints.NORTHWEST;
        c.insets = new Insets(4, 4, 4, 8);
        JLabel label = eu.amadeos.commons.i18n.Mnemonics.apply(new JLabel(),
                Messages.commons(Texts.messages().locale()).get("language.label"));
        label.setLabelFor(language);
        page.add(label, c);
        c.gridx = 1;
        c.weightx = 1;
        c.weighty = 1;
        page.add(language, c);
        return page;
    }

    /** Stores the language if it changed and says that it applies at the next start. */
    private void applyLanguage() {
        if (language.selectedLanguage().equals(locales.applicationLanguage())) return;
        try {
            locales.setApplicationLanguage(language.selectedLanguage());
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), getTitle(), JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (locales.restartRequired()) {
            JOptionPane.showMessageDialog(this, Messages.commons(Texts.messages().locale())
                    .format("language.restart", tr("app.title")), getTitle(), JOptionPane.INFORMATION_MESSAGE);
        }
    }

    /**
     * @param page        {@link #ACCOUNTS}, {@link #MESSAGE_VIEW} or {@link #LANGUAGE}
     * @param addAccount  start with a new, empty account (first start)
     */
    public void open(List<Account> current, MailPreferences preferences, String page, boolean addAccount) {
        accounts.setAccounts(current);
        view.setPreferences(preferences);
        category.setSelectedItem(page);
        cards.show(pages, page);
        if (addAccount) accounts.add();
        pack();
        setLocationRelativeTo(getOwner());
        setVisible(true);
    }
}
