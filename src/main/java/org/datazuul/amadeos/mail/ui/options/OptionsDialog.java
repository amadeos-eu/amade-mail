package org.datazuul.amadeos.mail.ui.options;

import org.datazuul.amadeos.mail.domain.Account;
import org.datazuul.amadeos.mail.domain.MailPreferences;
import org.datazuul.amadeos.mail.ui.swing.AmadeUI;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.Map;

/**
 * Mail Options, as in dtmail: a single dialog whose page is chosen with the "Category" menu.
 */
public final class OptionsDialog extends JDialog {
    public static final String ACCOUNTS = "Accounts";
    public static final String MESSAGE_VIEW = "Message View";

    /** Receives the settings when the user presses OK or Apply. */
    public interface ApplyHandler {
        void apply(List<Account> accounts, Map<String, String> passwords, MailPreferences preferences);
    }

    private final AccountsPanel accounts;
    private final MessageViewOptionsPanel view = new MessageViewOptionsPanel();
    private final JComboBox<String> category = new JComboBox<>(new String[]{ACCOUNTS, MESSAGE_VIEW});
    private final CardLayout cards = new CardLayout();
    private final JPanel pages = new JPanel(cards);

    public OptionsDialog(Frame owner, AccountsPanel.ConnectionTester tester, ApplyHandler handler) {
        super(owner, "Mailer - Mail Options", true);
        accounts = new AccountsPanel(tester);
        JPanel content = new JPanel(new BorderLayout(0, 10));
        content.setBackground(AmadeUI.BASE);
        content.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        top.setOpaque(false);
        top.add(new JLabel("Category:"));
        top.add(category);
        category.addActionListener(e -> cards.show(pages, (String) category.getSelectedItem()));
        content.add(top, BorderLayout.NORTH);

        pages.setOpaque(false);
        pages.add(accounts, ACCOUNTS);
        pages.add(view, MESSAGE_VIEW);
        content.add(pages, BorderLayout.CENTER);

        JButton ok = new JButton("OK");
        JButton apply = new JButton("Apply");
        JButton cancel = new JButton("Cancel");
        ok.addActionListener(e -> {
            handler.apply(accounts.accounts(), accounts.passwords(), view.preferences());
            dispose();
        });
        apply.addActionListener(e -> handler.apply(accounts.accounts(), accounts.passwords(), view.preferences()));
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

    /**
     * @param page        {@link #ACCOUNTS} or {@link #MESSAGE_VIEW}
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
