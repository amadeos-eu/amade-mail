package org.datazuul.amadeos.mail.ui.view;

import static org.datazuul.amadeos.mail.i18n.Texts.tr;

import org.datazuul.amadeos.mail.domain.MessageContent;
import org.datazuul.amadeos.mail.service.CalendarContent;
import org.datazuul.amadeos.mail.domain.MessageContent.Attachment;
import org.datazuul.amadeos.mail.ui.messages.MessageTableModel;
import org.datazuul.amadeos.commons.swing.AmadeUI;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * The lower half of the mailer: header block, remote-content bar, body (plain text or HTML) and
 * the attachment bar.
 */
public final class MessageViewPanel extends JPanel {
    /** The date in the header, e.g. "Tue, 13 Oct 2026 08:12", written the way the language writes it. */
    private static DateTimeFormatter dateFormat() {
        return DateTimeFormatter.ofPattern(tr("date.full"), org.datazuul.amadeos.mail.i18n.Texts.messages().locale())
                .withZone(ZoneId.systemDefault());
    }

    private final MessageViewActions actions;
    private final JPanel headers = new JPanel(new GridBagLayout());
    private final JPanel remoteBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
    private final JPanel calendarBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
    private final JLabel remoteLabel = new JLabel();
    private final CardLayout cards = new CardLayout();
    private final JPanel body = new JPanel(cards);
    private final JTextArea text = new JTextArea();
    private final HtmlMessagePane html;
    private final JPanel attachments = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 2));
    private final JPanel attachmentBar = new JPanel(new BorderLayout(8, 0));

    public MessageViewPanel(MessageViewActions actions) {
        super(new BorderLayout(0, 4));
        this.actions = actions;
        this.html = new HtmlMessagePane(actions::linkClicked);

        AmadeUI.style(headers, (panel, theme) -> panel.setBackground(theme.fieldBackground()));
        headers.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        AmadeUI.style(remoteBar, (panel, theme) -> panel.setBackground(theme.softHighlight()));
        remoteBar.add(remoteLabel);
        JButton once = new JButton(tr("view.load-images"));
        once.addActionListener(e -> actions.loadRemoteImagesOnce());
        JButton always = new JButton(tr("view.always-sender"));
        always.addActionListener(e -> actions.alwaysLoadRemoteImagesFromSender());
        remoteBar.add(once);
        remoteBar.add(always);
        remoteBar.setVisible(false);

        // A dtcm appointment text or an .ics attachment: one click schedules it, as a drop on the CDE
        // Calendar did.
        AmadeUI.style(calendarBar, (panel, theme) -> panel.setBackground(theme.softHighlight()));
        calendarBar.add(new JLabel(tr("calendar.contains")));
        JButton schedule = new JButton(tr("calendar.schedule"));
        schedule.addActionListener(e -> actions.scheduleInCalendar());
        calendarBar.add(schedule);
        calendarBar.setVisible(false);
        JPanel bars = new JPanel(new GridLayout(0, 1));
        bars.setOpaque(false);
        bars.add(remoteBar);
        bars.add(calendarBar);

        JPanel top = new JPanel(new BorderLayout());
        top.setBorder(AmadeUI.sunken());
        top.add(headers, BorderLayout.CENTER);
        top.add(bars, BorderLayout.SOUTH);

        text.setEditable(false);
        text.setLineWrap(true);
        text.setWrapStyleWord(true);
        text.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        text.setMargin(new Insets(6, 8, 6, 8));
        JScrollPane textScroll = new JScrollPane(text);
        textScroll.setBorder(BorderFactory.createEmptyBorder());
        body.add(textScroll, "text");
        body.add(html, "html");
        body.setBorder(AmadeUI.sunken());

        AmadeUI.style(attachmentBar, (panel, theme) -> panel.setBackground(theme.fieldBackground()));
        attachmentBar.setBorder(BorderFactory.createCompoundBorder(AmadeUI.sunken(),
                BorderFactory.createEmptyBorder(2, 8, 2, 8)));
        JLabel attachmentTitle = new JLabel(tr("menu.attachments").replace("&", ""));
        attachmentTitle.setFont(attachmentTitle.getFont().deriveFont(Font.PLAIN, 11f));
        attachmentBar.add(attachmentTitle, BorderLayout.WEST);
        attachments.setOpaque(false);
        attachmentBar.add(attachments, BorderLayout.CENTER);
        attachmentBar.setVisible(false);

        JPanel center = new JPanel(new BorderLayout(0, 4));
        center.setOpaque(false);
        center.add(top, BorderLayout.NORTH);
        center.add(body, BorderLayout.CENTER);
        add(center, BorderLayout.CENTER);
        add(attachmentBar, BorderLayout.SOUTH);
        clear("");
    }

    public void clear(String message) {
        headers.removeAll();
        headers.revalidate();
        headers.repaint();
        remoteBar.setVisible(false);
        calendarBar.setVisible(false);
        attachmentBar.setVisible(false);
        text.setText(message);
        cards.show(body, "text");
    }

    public void show(DisplayedMessage message) {
        MessageContent c = message.content();
        headers.removeAll();
        int row = 0;
        row = header(row, tr("header.from"), c.from());
        row = header(row, tr("header.date"), c.date() == null ? "" : dateFormat().format(c.date()));
        row = header(row, tr("header.to"), c.to());
        if (!c.cc().isBlank()) row = header(row, tr("header.cc"), c.cc());
        header(row, tr("header.subject"), c.subject());
        headers.revalidate();
        headers.repaint();

        boolean blocked = message.showsHtml() && message.html().hasBlockedRemoteContent();
        remoteLabel.setText(blocked
                ? tr("view.blocked", message.html().blockedRemote())
                : "");
        remoteBar.setVisible(blocked);
        calendarBar.setVisible(CalendarContent.hasAppointment(c, message.plainText()));

        if (message.showsHtml()) {
            try {
                html.show(message.html(), message.resources());
                cards.show(body, "html");
            } catch (RuntimeException e) {
                LoggerFactory.getLogger(MessageViewPanel.class).warn("HTML rendering failed", e);
                showText("[" + tr("view.html-failed", e.getMessage()) + "]\n\n" + message.plainText());
            }
        } else {
            showText(message.plainText());
        }

        attachments.removeAll();
        for (Attachment attachment : c.attachments()) attachments.add(attachmentButton(attachment));
        attachmentBar.setVisible(!c.attachments().isEmpty());
        attachments.revalidate();
        attachments.repaint();
    }

    /** Shows arbitrary text, e.g. the message source. */
    public void showText(String value) {
        text.setText(value);
        text.setCaretPosition(0);
        cards.show(body, "text");
    }

    /** The selected text of the plain view, for Edit → Copy. */
    public JTextArea textArea() {
        return text;
    }

    private int header(int row, String label, String value) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = row;
        c.anchor = GridBagConstraints.FIRST_LINE_START;
        c.insets = new Insets(1, 0, 1, 8);
        JLabel l = new JLabel(label);
        AmadeUI.style(l, (headerLabel, theme) -> headerLabel.setForeground(theme.textInactive()));
        l.setFont(l.getFont().deriveFont(Font.PLAIN));
        headers.add(l, c);
        c.gridx = 1;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(1, 0, 1, 0);
        JTextField v = new JTextField(value);
        v.setEditable(false);
        v.setBorder(BorderFactory.createEmptyBorder());
        // Read-only fields would get the panel colour; they sit on the white header block.
        AmadeUI.style(v, (field, theme) -> field.setBackground(theme.fieldBackground()));
        v.setFont(v.getFont().deriveFont(tr("header.subject").equals(label) ? Font.BOLD : Font.PLAIN));
        v.setCaretPosition(0);
        headers.add(v, c);
        return row + 1;
    }

    private JButton attachmentButton(Attachment attachment) {
        JButton button = new JButton(attachment.fileName() + "  (" + MessageTableModel.formatSize(attachment.size()) + ")",
                UIManager.getIcon("FileView.fileIcon"));
        button.setToolTipText(attachment.mimeType());
        JPopupMenu menu = new JPopupMenu();
        JMenuItem open = new JMenuItem(tr("attachment.open"));
        open.addActionListener(e -> actions.openAttachment(attachment));
        JMenuItem save = new JMenuItem(tr("attachment.save-as"));
        save.addActionListener(e -> actions.saveAttachment(attachment));
        menu.add(open);
        menu.add(save);
        button.addActionListener(e -> menu.show(button, 0, button.getHeight()));
        return button;
    }
}
