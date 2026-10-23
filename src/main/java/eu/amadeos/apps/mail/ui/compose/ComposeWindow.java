package eu.amadeos.apps.mail.ui.compose;

import static eu.amadeos.commons.i18n.Texts.tr;

import eu.amadeos.apps.mail.domain.Account;
import eu.amadeos.apps.mail.domain.OutgoingMessage;
import eu.amadeos.commons.contacts.AddressCompletion;
import eu.amadeos.commons.contacts.ContactDirectory;
import eu.amadeos.commons.swing.AmadeUI;

import javax.swing.*;
import javax.swing.border.BevelBorder;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The compose window ("New Message" in dtmail): From, To, Cc, optional Bcc, Subject, text and
 * attachments, with Send and Close. To, Cc and Bcc complete addresses from the address books of
 * amade-contacts.
 */
public final class ComposeWindow extends JFrame {

    /** Sends in the background and calls {@link #sent()} or {@link #failed(String)} on the EDT. */
    public interface SendHandler {
        void send(OutgoingMessage message, ComposeWindow window);
    }

    private final JComboBox<Account> from;
    private final JTextField to = new JTextField(40);
    private final JTextField cc = new JTextField(40);
    private final JTextField bcc = new JTextField(40);
    private final JLabel bccLabel = new JLabel(tr("header.bcc"));
    private final JTextField subject = new JTextField(40);
    private final JTextArea body = new JTextArea(20, 72);
    private final DefaultListModel<Path> attachments = new DefaultListModel<>();
    private final JList<Path> attachmentList = new JList<>(attachments);
    private final JScrollPane attachmentScroll = new JScrollPane(attachmentList);
    private final JLabel status = new JLabel(" ");
    private final JButton send = new JButton(tr("compose.send"));
    private final String inReplyTo;
    private final String initialBody;
    private final SendHandler handler;

    public ComposeWindow(List<Account> accounts, Draft draft, SendHandler handler, ContactDirectory contacts,
                         Locale locale) {
        super(tr("app.title.with", draft.subject().isBlank() ? tr("compose.new") : draft.subject()));
        this.handler = handler;
        this.inReplyTo = draft.inReplyTo();
        this.initialBody = draft.body();
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                close();
            }
        });

        from = new JComboBox<>(accounts.toArray(Account[]::new));
        from.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected,
                                                          boolean focus) {
                super.getListCellRendererComponent(list, value, index, selected, focus);
                if (value instanceof Account a) {
                    setText(a.displayName().isEmpty() ? a.email() : a.displayName() + " <" + a.email() + ">");
                    setIcon(AmadeUI.colorDot(a.color().color(), 10));
                }
                return this;
            }
        });
        for (Account a : accounts) if (a.id().equals(draft.accountId())) from.setSelectedItem(a);

        // --8<-- [start:completion]
        for (JTextField field : List.of(to, cc, bcc)) AddressCompletion.install(field, contacts, locale);
        // --8<-- [end:completion]
        to.setText(draft.to());
        cc.setText(draft.cc());
        subject.setText(draft.subject());
        body.setText(draft.body());
        body.setCaretPosition(0);
        body.setLineWrap(true);
        body.setWrapStyleWord(true);
        body.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        body.setMargin(new Insets(6, 8, 6, 8));

        JPanel fields = new JPanel(new GridBagLayout());
        fields.setOpaque(false);
        int row = 0;
        row = field(fields, row, new JLabel(tr("header.from")), from);
        row = field(fields, row, new JLabel(tr("header.to")), to);
        row = field(fields, row, new JLabel(tr("header.cc")), cc);
        row = field(fields, row, bccLabel, bcc);
        field(fields, row, new JLabel(tr("header.subject")), subject);
        bcc.setVisible(false);
        bccLabel.setVisible(false);

        JScrollPane bodyScroll = new JScrollPane(body);
        bodyScroll.setBorder(AmadeUI.sunken());

        attachmentList.setVisibleRowCount(2);
        attachmentList.setLayoutOrientation(JList.HORIZONTAL_WRAP);
        attachmentScroll.setBorder(AmadeUI.sunken());
        attachmentScroll.setVisible(false);

        send.addActionListener(e -> send());
        JButton closeButton = new JButton(tr("compose.close"));
        closeButton.addActionListener(e -> close());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        buttons.setOpaque(false);
        buttons.add(send);
        buttons.add(closeButton);

        JPanel south = new JPanel(new BorderLayout());
        south.setOpaque(false);
        south.add(attachmentScroll, BorderLayout.NORTH);
        south.add(buttons, BorderLayout.CENTER);
        JPanel statusBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        statusBar.setBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED));
        status.setFont(status.getFont().deriveFont(Font.PLAIN, 11f));
        statusBar.add(status);
        south.add(statusBar, BorderLayout.SOUTH);

        JPanel content = new JPanel(new BorderLayout(0, 6));
        content.setBorder(BorderFactory.createEmptyBorder(6, 8, 0, 8));
        content.add(fields, BorderLayout.NORTH);
        content.add(bodyScroll, BorderLayout.CENTER);
        content.add(south, BorderLayout.SOUTH);
        setContentPane(content);
        // Files dropped anywhere on the window become attachments, e.g. the Drag Appt icon of the Calendar.
        TransferHandler drop = new TransferHandler() {
            @Override
            public boolean canImport(TransferSupport support) {
                return support.isDataFlavorSupported(java.awt.datatransfer.DataFlavor.javaFileListFlavor);
            }

            @Override
            @SuppressWarnings("unchecked")
            public boolean importData(TransferSupport support) {
                try {
                    List<File> files = (List<File>) support.getTransferable()
                            .getTransferData(java.awt.datatransfer.DataFlavor.javaFileListFlavor);
                    for (File f : files) if (f.isFile()) attachments.addElement(f.toPath());
                    attachmentScroll.setVisible(!attachments.isEmpty());
                    revalidate();
                    return true;
                } catch (java.awt.datatransfer.UnsupportedFlavorException | java.io.IOException e) {
                    return false;
                }
            }
        };
        content.setTransferHandler(drop);
        body.setTransferHandler(drop);
        attachmentList.setTransferHandler(drop);
        setJMenuBar(menuBar());
        pack();
        setLocationByPlatform(true);
    }

    private JMenuBar menuBar() {
        JMenuBar bar = new JMenuBar();
        JMenu file = eu.amadeos.commons.i18n.Mnemonics.apply(new JMenu(), tr("compose.menu.file"));
        file.add(item(tr("compose.send"), KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, KeyEvent.CTRL_DOWN_MASK), this::send));
        file.addSeparator();
        file.add(item(tr("compose.close"), KeyStroke.getKeyStroke(KeyEvent.VK_W, KeyEvent.CTRL_DOWN_MASK), this::close));
        JMenu attach = eu.amadeos.commons.i18n.Mnemonics.apply(new JMenu(), tr("menu.attachments"));
        attach.add(item(tr("compose.attach.add"), null, this::addAttachment));
        attach.add(item(tr("compose.attach.delete"), null, this::removeAttachment));
        JMenu format = eu.amadeos.commons.i18n.Mnemonics.apply(new JMenu(), tr("compose.menu.format"));
        JCheckBoxMenuItem showBcc = new JCheckBoxMenuItem(tr("compose.show-bcc"));
        showBcc.addActionListener(e -> {
            bcc.setVisible(showBcc.isSelected());
            bccLabel.setVisible(showBcc.isSelected());
            revalidate();
        });
        format.add(showBcc);
        bar.add(file);
        bar.add(attach);
        bar.add(format);
        return bar;
    }

    private static JMenuItem item(String text, KeyStroke accelerator, Runnable action) {
        JMenuItem item = new JMenuItem(text);
        if (accelerator != null) item.setAccelerator(accelerator);
        item.addActionListener(e -> action.run());
        return item;
    }

    private static int field(JPanel panel, int row, JLabel label, JComponent component) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = row;
        c.anchor = GridBagConstraints.LINE_END;
        c.insets = new Insets(2, 0, 2, 8);
        panel.add(label, c);
        c.gridx = 1;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(2, 0, 2, 0);
        panel.add(component, c);
        return row + 1;
    }

    private void addAttachment() {
        JFileChooser chooser = new JFileChooser();
        chooser.setMultiSelectionEnabled(true);
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        for (File f : chooser.getSelectedFiles()) attachments.addElement(f.toPath());
        attachmentScroll.setVisible(!attachments.isEmpty());
        revalidate();
    }

    private void removeAttachment() {
        for (Path p : attachmentList.getSelectedValuesList()) attachments.removeElement(p);
        attachmentScroll.setVisible(!attachments.isEmpty());
        revalidate();
    }

    private void send() {
        Account account = (Account) from.getSelectedItem();
        if (account == null) return;
        if (subject.getText().isBlank()) {
            int answer = JOptionPane.showConfirmDialog(this, tr("compose.no-subject"), tr("app.title"),
                    JOptionPane.OK_CANCEL_OPTION);
            if (answer != JOptionPane.OK_OPTION) return;
        }
        List<Path> files = new ArrayList<>();
        for (int i = 0; i < attachments.size(); i++) files.add(attachments.get(i));
        OutgoingMessage message = new OutgoingMessage(account, to.getText(), cc.getText(),
                bcc.isVisible() ? bcc.getText() : "", subject.getText(), body.getText(), files, inReplyTo);
        send.setEnabled(false);
        status.setText(tr("compose.sending", account.smtp().host()));
        handler.send(message, this);
    }

    /** Called by the presenter after a successful send. */
    public void sent() {
        dispose();
    }

    /** Called by the presenter if sending failed; the message stays open for another try. */
    public void failed(String message) {
        send.setEnabled(true);
        status.setText(tr("compose.not-sent", message));
        JOptionPane.showMessageDialog(this, message, tr("app.title.with", tr("compose.failed")), JOptionPane.ERROR_MESSAGE);
    }

    private void close() {
        boolean changed = !body.getText().equals(initialBody) || !attachments.isEmpty();
        if (changed) {
            int answer = JOptionPane.showConfirmDialog(this, tr("compose.discard"), tr("app.title"),
                    JOptionPane.OK_CANCEL_OPTION);
            if (answer != JOptionPane.OK_OPTION) return;
        }
        dispose();
    }

    public void focusBody() {
        if (to.getText().isBlank()) to.requestFocusInWindow();
        else body.requestFocusInWindow();
    }
}
