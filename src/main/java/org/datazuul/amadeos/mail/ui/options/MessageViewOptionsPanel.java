package org.datazuul.amadeos.mail.ui.options;

import org.datazuul.amadeos.mail.domain.MailPreferences;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * "Message View" category of Mail Options.
 */
public final class MessageViewOptionsPanel extends JPanel {
    private final JRadioButton plain = new JRadioButton("Plain text");
    private final JRadioButton html = new JRadioButton("HTML (sanitised, without scripts)");
    private final JCheckBox alwaysRemote = new JCheckBox("Always load remote images (allows tracking)");
    private final JTextArea trusted = new JTextArea(5, 30);
    private final JSpinner interval = new JSpinner(new SpinnerNumberModel(5, 0, 1440, 1));

    public MessageViewOptionsPanel() {
        super(new GridBagLayout());
        setOpaque(false);
        ButtonGroup group = new ButtonGroup();
        group.add(plain);
        group.add(html);
        for (AbstractButton b : new AbstractButton[]{plain, html, alwaysRemote}) b.setOpaque(false);

        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.anchor = GridBagConstraints.LINE_START;
        c.insets = new Insets(3, 0, 3, 0);
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        add(new JLabel("Show messages that contain both formats as:"), c);
        add(plain, c);
        add(html, c);
        c.insets = new Insets(12, 0, 3, 0);
        add(alwaysRemote, c);
        c.insets = new Insets(3, 0, 3, 0);
        add(new JLabel("Always load remote images from these senders (one address per line):"), c);
        trusted.setLineWrap(false);
        add(new JScrollPane(trusted), c);
        JPanel intervalRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        intervalRow.setOpaque(false);
        intervalRow.add(new JLabel("Check for new mail every"));
        intervalRow.add(interval);
        intervalRow.add(new JLabel("minutes (0 = never)"));
        c.insets = new Insets(12, 0, 3, 0);
        add(intervalRow, c);
        c.weighty = 1;
        add(Box.createVerticalGlue(), c);
    }

    public void setPreferences(MailPreferences p) {
        (p.preferHtml() ? html : plain).setSelected(true);
        alwaysRemote.setSelected(p.alwaysLoadRemoteImages());
        trusted.setText(String.join("\n", p.trustedImageSenders()));
        interval.setValue(p.checkIntervalMinutes());
    }

    public MailPreferences preferences() {
        Set<String> senders = Arrays.stream(trusted.getText().split("[\\s,;]+"))
                .map(String::strip)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
        return new MailPreferences(html.isSelected(), alwaysRemote.isSelected(), senders, (int) interval.getValue());
    }
}
