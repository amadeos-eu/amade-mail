package org.datazuul.amadeos.mail.ui.options;

import static org.datazuul.amadeos.mail.i18n.Texts.tr;

import org.datazuul.amadeos.mail.domain.Account;
import org.datazuul.amadeos.mail.domain.AccountColor;
import org.datazuul.amadeos.mail.domain.AuthMethod;
import org.datazuul.amadeos.mail.domain.ConnectionSecurity;
import org.datazuul.amadeos.mail.domain.ServerSettings;
import org.datazuul.amadeos.mail.service.ServerGuesser;
import org.datazuul.amadeos.commons.swing.AmadeUI;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * "Accounts" category of Mail Options: the account list on the left, the settings of the selected
 * account on the right.
 */
public final class AccountsPanel extends JPanel {

    /** Checks the settings in the background and reports the result on the EDT. */
    public interface ConnectionTester {
        void test(Account account, String passwordOrNull, Consumer<String> result);
    }

    private final ServerGuesser guesser = new ServerGuesser();
    private final ConnectionTester tester;
    private final DefaultListModel<Account> listModel = new DefaultListModel<>();
    private final JList<Account> list = new JList<>(listModel);
    private final Map<String, String> passwords = new HashMap<>();

    private final JTextField name = new JTextField(24);
    private final JTextField displayName = new JTextField(24);
    private final JTextField email = new JTextField(24);
    private final JTextField username = new JTextField(24);
    private final JComboBox<AuthMethod> auth = new JComboBox<>(AuthMethod.values());
    private final JPasswordField password = new JPasswordField(24);
    private final ServerFields imap = new ServerFields(true);
    private final ServerFields smtp = new ServerFields(false);
    private final JTextArea signature = new JTextArea(3, 24);
    private final JComboBox<AccountColor> color = new JComboBox<>(AccountColor.values());
    private final JLabel hint = new JLabel(" ");
    private final JPanel form = new JPanel(new GridBagLayout());
    private Account editing;
    private boolean loading;

    public AccountsPanel(ConnectionTester tester) {
        super(new BorderLayout(10, 0));
        this.tester = tester;
        setOpaque(false);

        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> l, Object value, int index, boolean selected,
                                                          boolean focus) {
                super.getListCellRendererComponent(l, value, index, selected, focus);
                Account a = (Account) value;
                setText(a.label());
                setIcon(AmadeUI.colorDot(a.color().color(), 10));
                return this;
            }
        });
        list.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && !loading) {
                commit();
                edit(list.getSelectedValue());
            }
        });
        JScrollPane listScroll = new JScrollPane(list);
        listScroll.setBorder(AmadeUI.sunken());
        listScroll.setPreferredSize(new Dimension(160, 200));

        JPanel listButtons = new JPanel(new GridLayout(0, 1, 0, 4));
        listButtons.setOpaque(false);
        listButtons.add(button(tr("accounts.add"), this::add));
        listButtons.add(button(tr("accounts.remove"), this::remove));
        listButtons.add(button(tr("accounts.up"), () -> move(-1)));
        listButtons.add(button(tr("accounts.down"), () -> move(1)));
        JPanel left = new JPanel(new BorderLayout(0, 6));
        left.setOpaque(false);
        left.add(listScroll, BorderLayout.CENTER);
        left.add(listButtons, BorderLayout.SOUTH);
        add(left, BorderLayout.WEST);

        form.setOpaque(false);
        int row = 0;
        row = field(row, tr("accounts.name"), name);
        row = field(row, tr("accounts.display-name"), displayName);
        JPanel emailRow = new JPanel(new BorderLayout(6, 0));
        emailRow.setOpaque(false);
        emailRow.add(email, BorderLayout.CENTER);
        emailRow.add(button(tr("accounts.guess"), this::guess), BorderLayout.EAST);
        row = field(row, tr("accounts.email"), emailRow);
        row = field(row, tr("accounts.imap"), imap);
        row = field(row, tr("accounts.smtp"), smtp);
        row = field(row, tr("accounts.username"), username);
        row = field(row, tr("accounts.auth"), auth);
        row = field(row, tr("accounts.password"), password);
        signature.setLineWrap(true);
        JScrollPane signatureScroll = new JScrollPane(signature);
        signatureScroll.setBorder(AmadeUI.sunken());
        row = field(row, tr("accounts.signature"), signatureScroll);
        row = field(row, tr("accounts.color"), color);
        JPanel test = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        test.setOpaque(false);
        test.add(button(tr("accounts.test"), this::testConnection));
        row = field(row, "", test);
        hint.setFont(hint.getFont().deriveFont(Font.PLAIN, 11f));
        field(row, "", hint);
        password.setToolTipText(tr("accounts.password.keep"));
        auth.addActionListener(e -> {
            updatePasswordField();
            if (!loading && selectedAuth().isOAuth2()) {
                hint.setText(tr("accounts.hint.oauth", selectedAuth().provider().label()));
            }
        });
        add(form, BorderLayout.CENTER);
        setFormEnabled(false);
    }

    public void setAccounts(List<Account> accounts) {
        loading = true;
        try {
            listModel.clear();
            accounts.forEach(listModel::addElement);
            passwords.clear();
            editing = null;
        } finally {
            loading = false;
        }
        if (!listModel.isEmpty()) list.setSelectedIndex(0);
        else edit(null);
    }

    /** The edited accounts in list order. */
    public List<Account> accounts() {
        commit();
        List<Account> result = new ArrayList<>();
        for (int i = 0; i < listModel.size(); i++) result.add(listModel.get(i));
        return result;
    }

    /** Passwords entered in this dialog, by account id. */
    public Map<String, String> passwords() {
        commit();
        return Map.copyOf(passwords);
    }

    /** Adds a new account and selects it (used when the mailer starts without accounts). */
    public void add() {
        commit();
        Account account = Account.blank(AccountColor.forIndex(listModel.size()));
        listModel.addElement(account);
        list.setSelectedIndex(listModel.size() - 1);
        email.requestFocusInWindow();
        hint.setText(tr("accounts.hint.new"));
    }

    private void remove() {
        int index = list.getSelectedIndex();
        if (index < 0) return;
        Account account = listModel.get(index);
        int answer = JOptionPane.showConfirmDialog(this, tr("accounts.remove.confirm", account.label()),
                tr("accounts.remove.title"), JOptionPane.OK_CANCEL_OPTION);
        if (answer != JOptionPane.OK_OPTION) return;
        loading = true;
        try {
            editing = null;
            listModel.remove(index);
            passwords.remove(account.id());
        } finally {
            loading = false;
        }
        if (!listModel.isEmpty()) list.setSelectedIndex(Math.min(index, listModel.size() - 1));
        else edit(null);
    }

    private void move(int delta) {
        commit();
        int index = list.getSelectedIndex();
        int target = index + delta;
        if (index < 0 || target < 0 || target >= listModel.size()) return;
        loading = true;
        try {
            Account account = listModel.remove(index);
            listModel.add(target, account);
        } finally {
            loading = false;
        }
        list.setSelectedIndex(target);
    }

    private void guess() {
        guesser.guess(email.getText()).ifPresentOrElse(g -> {
            imap.set(g.imap());
            smtp.set(g.smtp());
            if (username.getText().isBlank()) username.setText(g.username());
            auth.setSelectedItem(g.auth());
            if (name.getText().isBlank() || tr("account.new").equals(name.getText())) {
                String address = email.getText().strip();
                name.setText(address.substring(address.indexOf('@') + 1));
            }
            hint.setText(g.hint().isEmpty() ? " " : g.hint());
        }, () -> hint.setText(tr("accounts.hint.complete-address")));
    }

    private void testConnection() {
        commit();
        if (editing == null) return;
        if (!editing.isComplete()) {
            hint.setText(tr("accounts.hint.incomplete"));
            return;
        }
        hint.setText(tr(editing.auth().isOAuth2() ? "accounts.testing.oauth" : "accounts.testing",
                editing.imap().host(), editing.smtp().host()));
        Account tested = editing;
        tester.test(tested, passwords.get(tested.id()), message -> {
            if (editing != null && editing.id().equals(tested.id())) hint.setText(message);
        });
    }

    private void edit(Account account) {
        editing = account;
        loading = true;
        try {
            Account a = account != null ? account : Account.blank(AccountColor.BLUE);
            name.setText(a.name());
            displayName.setText(a.displayName());
            email.setText(a.email());
            username.setText(a.username());
            auth.setSelectedItem(a.auth());
            password.setText(passwords.getOrDefault(a.id(), ""));
            imap.set(a.imap());
            smtp.set(a.smtp());
            signature.setText(a.signature());
            color.setSelectedItem(a.color());
            hint.setText(" ");
        } finally {
            loading = false;
        }
        setFormEnabled(account != null);
    }

    /** Writes the form back into the list. */
    private void commit() {
        if (editing == null || loading) return;
        int index = indexOf(editing.id());
        if (index < 0) return;
        Account updated = new Account(editing.id(), name.getText(), displayName.getText(), email.getText(),
                username.getText(), imap.get(), smtp.get(), signature.getText(), (AccountColor) color.getSelectedItem(),
                selectedAuth());
        String typed = new String(password.getPassword());
        if (!typed.isEmpty() && !updated.auth().isOAuth2()) passwords.put(editing.id(), typed);
        editing = updated;
        loading = true;
        try {
            listModel.set(index, updated);
        } finally {
            loading = false;
        }
    }

    private int indexOf(String id) {
        for (int i = 0; i < listModel.size(); i++) if (listModel.get(i).id().equals(id)) return i;
        return -1;
    }

    private void setFormEnabled(boolean enabled) {
        for (JComponent c : List.of(name, displayName, email, username, auth, signature, color)) {
            c.setEnabled(enabled);
        }
        imap.setEnabled(enabled);
        smtp.setEnabled(enabled);
        updatePasswordField();
    }

    private AuthMethod selectedAuth() {
        return (AuthMethod) auth.getSelectedItem();
    }

    /** OAuth2 accounts have no password; the token comes from the browser sign-in. */
    private void updatePasswordField() {
        password.setEnabled(editing != null && !selectedAuth().isOAuth2());
    }

    private int field(int row, String label, JComponent component) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = row;
        c.anchor = GridBagConstraints.LINE_END;
        c.insets = new Insets(3, 0, 3, 8);
        form.add(new JLabel(label), c);
        c.gridx = 1;
        c.weightx = 1;
        c.anchor = GridBagConstraints.LINE_START;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(3, 0, 3, 0);
        form.add(component, c);
        return row + 1;
    }

    private static JButton button(String text, Runnable action) {
        JButton button = new JButton(text);
        button.addActionListener(e -> action.run());
        return button;
    }

    /** Host, port and security of one server in a single row. */
    private static final class ServerFields extends JPanel {
        private final boolean imap;
        private final JTextField host = new JTextField(18);
        private final JSpinner port = new JSpinner(new SpinnerNumberModel(993, 1, 65535, 1));
        private final JComboBox<ConnectionSecurity> security = new JComboBox<>(ConnectionSecurity.values());
        private ConnectionSecurity previous = ConnectionSecurity.TLS;

        ServerFields(boolean imap) {
            super(new BorderLayout(6, 0));
            this.imap = imap;
            setOpaque(false);
            port.setEditor(new JSpinner.NumberEditor(port, "#"));
            JPanel right = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
            right.setOpaque(false);
            right.add(port);
            right.add(security);
            add(host, BorderLayout.CENTER);
            add(right, BorderLayout.EAST);
            security.addActionListener(e -> {
                ConnectionSecurity selected = (ConnectionSecurity) security.getSelectedItem();
                // Follow the default port of the new security mode unless the user chose a custom port.
                if (selected != null && (int) port.getValue() == defaultPort(previous)) port.setValue(defaultPort(selected));
                previous = selected;
            });
        }

        private int defaultPort(ConnectionSecurity s) {
            return imap ? s.defaultImapPort() : s.defaultSmtpPort();
        }

        void set(ServerSettings settings) {
            host.setText(settings.host());
            previous = settings.security();
            security.setSelectedItem(settings.security());
            port.setValue(settings.port() > 0 ? settings.port() : defaultPort(settings.security()));
        }

        ServerSettings get() {
            return new ServerSettings(host.getText(), (int) port.getValue(), (ConnectionSecurity) security.getSelectedItem());
        }

        @Override
        public void setEnabled(boolean enabled) {
            super.setEnabled(enabled);
            host.setEnabled(enabled);
            port.setEnabled(enabled);
            security.setEnabled(enabled);
        }
    }
}
