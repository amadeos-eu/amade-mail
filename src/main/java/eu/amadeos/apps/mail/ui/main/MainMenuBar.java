package eu.amadeos.apps.mail.ui.main;

import static eu.amadeos.commons.i18n.Texts.tr;

import eu.amadeos.apps.mail.domain.MailFolder;
import eu.amadeos.apps.mail.ui.options.OptionsDialog;

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
    private final JRadioButtonMenuItem plainView = new JRadioButtonMenuItem(tr("menu.view.plain"));
    private final JRadioButtonMenuItem htmlView = new JRadioButtonMenuItem(tr("menu.view.html"));

    MainMenuBar(MainPresenter presenter, MainWindow window) {
        int ctrl = InputEvent.CTRL_DOWN_MASK;
        int ctrlShift = InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK;

        JMenu mailbox = menu("menu.mailbox");
        mailbox.add(item(tr("menu.mailbox.check"), KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0), presenter::checkMail));
        mailbox.addSeparator();
        mailbox.add(item(tr("menu.mailbox.options"), null, () -> presenter.openOptions(OptionsDialog.ACCOUNTS, false)));
        mailbox.addSeparator();
        mailbox.add(item(tr("menu.mailbox.close"), KeyStroke.getKeyStroke(KeyEvent.VK_Q, ctrl), presenter::exit));
        add(mailbox);

        JMenu message = menu("menu.message");
        message.add(item(tr("menu.message.unread"), KeyStroke.getKeyStroke(KeyEvent.VK_U, ctrl), presenter::markUnread));
        message.add(item(tr("menu.message.delete"), KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), presenter::deleteSelected));
        message.addSeparator();
        message.add(item(tr("menu.message.source"), KeyStroke.getKeyStroke(KeyEvent.VK_U, ctrlShift), presenter::showSource));
        add(message);

        JMenu edit = menu("menu.edit");
        edit.add(item(tr("menu.edit.copy"), KeyStroke.getKeyStroke(KeyEvent.VK_C, ctrl), window::copySelection));
        edit.add(item(tr("menu.edit.select-all"), KeyStroke.getKeyStroke(KeyEvent.VK_A, ctrl), window::selectAllMessages));
        add(edit);

        JMenu attachments = menu("menu.attachments");
        attachments.add(item(tr("menu.attachments.save-all"), null, window::saveAllAttachments));
        add(attachments);

        JMenu view = menu("menu.view");
        view.add(item(tr("menu.view.next"), KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, InputEvent.ALT_DOWN_MASK), presenter::next));
        view.add(item(tr("menu.view.previous"), KeyStroke.getKeyStroke(KeyEvent.VK_UP, InputEvent.ALT_DOWN_MASK), presenter::previous));
        view.addSeparator();
        ButtonGroup format = new ButtonGroup();
        format.add(plainView);
        format.add(htmlView);
        plainView.addActionListener(e -> presenter.setShowHtml(false));
        htmlView.addActionListener(e -> presenter.setShowHtml(true));
        view.add(plainView);
        view.add(htmlView);
        view.addSeparator();
        JCheckBoxMenuItem folders = new JCheckBoxMenuItem(tr("menu.view.folders"), true);
        folders.addActionListener(e -> window.setFoldersVisible(folders.isSelected()));
        view.add(folders);
        add(view);

        JMenu compose = menu("menu.compose");
        compose.add(item(tr("menu.compose.new"), KeyStroke.getKeyStroke(KeyEvent.VK_N, ctrl), presenter::composeNew));
        compose.addSeparator();
        compose.add(item(tr("menu.compose.reply"), KeyStroke.getKeyStroke(KeyEvent.VK_R, ctrl), () -> presenter.reply(false)));
        compose.add(item(tr("menu.compose.reply-all"), KeyStroke.getKeyStroke(KeyEvent.VK_R, ctrlShift), () -> presenter.reply(true)));
        compose.add(item(tr("menu.compose.forward"), KeyStroke.getKeyStroke(KeyEvent.VK_L, ctrl), presenter::forward));
        add(compose);

        JMenu move = menu("menu.move");
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
        JMenu help = menu("menu.help");
        help.add(item(tr("menu.help.about"), null, window::showAbout));
        add(help);
    }

    void setShowHtml(boolean html) {
        (html ? htmlView : plainView).setSelected(true);
    }

    private static void fillMove(JMenu move, List<MailFolder> targets, Consumer<MailFolder> action) {
        move.removeAll();
        if (targets.isEmpty()) {
            JMenuItem none = new JMenuItem(tr("menu.move.none"));
            none.setEnabled(false);
            move.add(none);
            return;
        }
        for (MailFolder folder : targets) {
            move.add(item(folder.fullName(), null, () -> action.accept(folder)));
        }
    }

    /** A menu titled with {@code key}; {@code &} in the text marks the mnemonic letter. */
    private static JMenu menu(String key) {
        return eu.amadeos.commons.i18n.Mnemonics.apply(new JMenu(), tr(key));
    }

    private static JMenuItem item(String text, KeyStroke accelerator, Runnable action) {
        JMenuItem item = new JMenuItem(text);
        if (accelerator != null) item.setAccelerator(accelerator);
        item.addActionListener(e -> action.run());
        return item;
    }
}
