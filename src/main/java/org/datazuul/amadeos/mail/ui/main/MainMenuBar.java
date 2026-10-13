package org.datazuul.amadeos.mail.ui.main;

import org.datazuul.amadeos.mail.domain.MailFolder;
import org.datazuul.amadeos.mail.ui.options.OptionsDialog;

import javax.swing.*;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.function.Consumer;

/**
 * The dtmail menu bar: Mailbox, Message, Edit, Attachments, View, Compose, Move and Help.
 */
final class MainMenuBar extends JMenuBar {
    private final JRadioButtonMenuItem plainView = new JRadioButtonMenuItem("Plain Text");
    private final JRadioButtonMenuItem htmlView = new JRadioButtonMenuItem("HTML");

    MainMenuBar(MainPresenter presenter, MainWindow window) {
        int ctrl = InputEvent.CTRL_DOWN_MASK;
        int ctrlShift = InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK;

        JMenu mailbox = menu("Mailbox", KeyEvent.VK_M);
        mailbox.add(item("Check for New Mail", KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0), presenter::checkMail));
        mailbox.addSeparator();
        mailbox.add(item("Mail Options…", null, () -> presenter.openOptions(OptionsDialog.ACCOUNTS, false)));
        mailbox.addSeparator();
        mailbox.add(item("Close", KeyStroke.getKeyStroke(KeyEvent.VK_Q, ctrl), presenter::exit));
        add(mailbox);

        JMenu message = menu("Message", KeyEvent.VK_E);
        message.add(item("Mark as Unread", KeyStroke.getKeyStroke(KeyEvent.VK_U, ctrl), presenter::markUnread));
        message.add(item("Delete", KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), presenter::deleteSelected));
        message.addSeparator();
        message.add(item("Show Source", KeyStroke.getKeyStroke(KeyEvent.VK_U, ctrlShift), presenter::showSource));
        add(message);

        JMenu edit = menu("Edit", KeyEvent.VK_D);
        edit.add(item("Copy", KeyStroke.getKeyStroke(KeyEvent.VK_C, ctrl), window::copySelection));
        edit.add(item("Select All", KeyStroke.getKeyStroke(KeyEvent.VK_A, ctrl), window::selectAllMessages));
        add(edit);

        JMenu attachments = menu("Attachments", KeyEvent.VK_A);
        attachments.add(item("Save All…", null, window::saveAllAttachments));
        add(attachments);

        JMenu view = menu("View", KeyEvent.VK_V);
        view.add(item("Next", KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, InputEvent.ALT_DOWN_MASK), presenter::next));
        view.add(item("Previous", KeyStroke.getKeyStroke(KeyEvent.VK_UP, InputEvent.ALT_DOWN_MASK), presenter::previous));
        view.addSeparator();
        ButtonGroup format = new ButtonGroup();
        format.add(plainView);
        format.add(htmlView);
        plainView.addActionListener(e -> presenter.setShowHtml(false));
        htmlView.addActionListener(e -> presenter.setShowHtml(true));
        view.add(plainView);
        view.add(htmlView);
        view.addSeparator();
        JCheckBoxMenuItem folders = new JCheckBoxMenuItem("Show Folders", true);
        folders.addActionListener(e -> window.setFoldersVisible(folders.isSelected()));
        view.add(folders);
        add(view);

        JMenu compose = menu("Compose", KeyEvent.VK_C);
        compose.add(item("New Message", KeyStroke.getKeyStroke(KeyEvent.VK_N, ctrl), presenter::composeNew));
        compose.addSeparator();
        compose.add(item("Reply to Sender", KeyStroke.getKeyStroke(KeyEvent.VK_R, ctrl), () -> presenter.reply(false)));
        compose.add(item("Reply to All", KeyStroke.getKeyStroke(KeyEvent.VK_R, ctrlShift), () -> presenter.reply(true)));
        compose.add(item("Forward", KeyStroke.getKeyStroke(KeyEvent.VK_L, ctrl), presenter::forward));
        add(compose);

        JMenu move = menu("Move", KeyEvent.VK_O);
        move.addMenuListener(new MenuListener() {
            @Override
            public void menuSelected(MenuEvent e) {
                fillMove(move, presenter.moveTargets(), presenter::moveSelected);
            }

            @Override
            public void menuDeselected(MenuEvent e) {
            }

            @Override
            public void menuCanceled(MenuEvent e) {
            }
        });
        add(move);

        add(Box.createHorizontalGlue());
        JMenu help = menu("Help", KeyEvent.VK_H);
        help.add(item("About Mailer", null, window::showAbout));
        add(help);
    }

    void setShowHtml(boolean html) {
        (html ? htmlView : plainView).setSelected(true);
    }

    private static void fillMove(JMenu move, List<MailFolder> targets, Consumer<MailFolder> action) {
        move.removeAll();
        if (targets.isEmpty()) {
            JMenuItem none = new JMenuItem("(select a message first)");
            none.setEnabled(false);
            move.add(none);
            return;
        }
        for (MailFolder folder : targets) {
            move.add(item(folder.fullName(), null, () -> action.accept(folder)));
        }
    }

    private static JMenu menu(String text, int mnemonic) {
        JMenu menu = new JMenu(text);
        menu.setMnemonic(mnemonic);
        return menu;
    }

    private static JMenuItem item(String text, KeyStroke accelerator, Runnable action) {
        JMenuItem item = new JMenuItem(text);
        if (accelerator != null) item.setAccelerator(accelerator);
        item.addActionListener(e -> action.run());
        return item;
    }
}
