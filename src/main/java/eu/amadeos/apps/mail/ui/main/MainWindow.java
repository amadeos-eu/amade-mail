package eu.amadeos.apps.mail.ui.main;

import com.google.inject.Inject;
import eu.amadeos.commons.contacts.ContactDirectory;
import eu.amadeos.commons.desktop.Notification;
import eu.amadeos.commons.desktop.Notifications;
import eu.amadeos.commons.i18n.LocaleSettings;
import static eu.amadeos.commons.i18n.Texts.tr;

import eu.amadeos.apps.mail.domain.Account;
import eu.amadeos.apps.mail.domain.MailPreferences;
import eu.amadeos.apps.mail.domain.MessageContent.Attachment;
import eu.amadeos.apps.mail.domain.MessageSummary;
import eu.amadeos.commons.swing.EdtExecutor;
import eu.amadeos.apps.mail.ui.compose.ComposeWindow;
import eu.amadeos.apps.mail.ui.compose.Draft;
import eu.amadeos.apps.mail.ui.folders.AccountState;
import eu.amadeos.apps.mail.ui.folders.FolderTreePanel;
import eu.amadeos.apps.mail.ui.messages.MessageListPanel;
import eu.amadeos.apps.mail.ui.options.OptionsDialog;
import eu.amadeos.commons.swing.AmadeUI;
import eu.amadeos.apps.mail.ui.view.DisplayedMessage;
import eu.amadeos.apps.mail.ui.view.MessageViewActions;
import eu.amadeos.apps.mail.ui.view.MessageViewPanel;

import javax.swing.*;
import javax.swing.border.BevelBorder;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The mailer window: folder column on the left; on the right the header list with the button row
 * above the message view, as in the CDE Mailer.
 */
public final class MainWindow extends JFrame implements MainView {
    private long newMailNotification;
    private final FolderTreePanel folders = new FolderTreePanel();
    private final MessageListPanel list = new MessageListPanel();
    private final MessageViewPanel messageView;
    private final JSplitPane horizontal;
    private final JLabel status = new JLabel(" ");
    private MainPresenter presenter;
    private MainMenuBar menuBar;
    private DisplayedMessage displayed;
    private List<Account> accounts = List.of();
    private int dividerLocation = 210;

    private final LocaleSettings locales;
    private final ContactDirectory contacts;

    @Inject
    public MainWindow(LocaleSettings locales, ContactDirectory contacts) {
        super(tr("app.title"));
        this.locales = locales;
        this.contacts = contacts;
        EdtExecutor.requireEdt();
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setIconImage(new ImageIcon(getClass().getResource("/icons/amade-mail.png")).getImage());

        messageView = new MessageViewPanel(new MessageViewActions() {
            @Override
            public void loadRemoteImagesOnce() {
                presenter.loadRemoteImagesOnce();
            }

            @Override
            public void alwaysLoadRemoteImagesFromSender() {
                presenter.alwaysLoadRemoteImagesFromSender();
            }

            @Override
            public void linkClicked(String uri) {
                presenter.linkClicked(uri);
            }

            @Override
            public void openAttachment(Attachment attachment) {
                presenter.openAttachment(attachment);
            }

            @Override
            public void saveAttachment(Attachment attachment) {
                presenter.saveAttachment(attachment);
            }

            @Override
            public void scheduleInCalendar() {
                presenter.scheduleInCalendar();
            }
        });

        JSplitPane vertical = new JSplitPane(JSplitPane.VERTICAL_SPLIT, list, messageView);
        vertical.setResizeWeight(0.4);
        vertical.setBorder(BorderFactory.createEmptyBorder());
        vertical.setContinuousLayout(true);
        horizontal = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, folders, vertical);
        horizontal.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        horizontal.setContinuousLayout(true);
        horizontal.setDividerLocation(dividerLocation);

        JPanel statusBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 3));
        statusBar.setBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED));
        status.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        statusBar.add(status);

        JPanel content = new JPanel(new BorderLayout());
        content.add(horizontal, BorderLayout.CENTER);
        content.add(statusBar, BorderLayout.SOUTH);
        setContentPane(content);
        setPreferredSize(new Dimension(1100, 760));
        pack();
        setLocationRelativeTo(null);
    }

    public void bind(MainPresenter presenter) {
        EdtExecutor.requireEdt();
        this.presenter = presenter;
        menuBar = new MainMenuBar(presenter, this);
        setJMenuBar(menuBar);
        folders.setSelectionListener(presenter::folderSelected);
        list.setSelectionListener(presenter::messageSelected);
        list.setAccountColors(id -> accounts.stream().filter(a -> a.id().equals(id)).findFirst()
                .map(a -> a.color().color()).orElse(null));
        list.addButton(tr("menu.message.delete"), presenter::deleteSelected);
        list.addButton(tr("menu.view.next"), presenter::next);
        list.addButton(tr("menu.view.previous"), presenter::previous);
        list.addButton(tr("menu.compose.reply"), () -> presenter.reply(false));
        list.addButton(tr("menu.compose.forward"), presenter::forward);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                presenter.exit();
            }
        });
    }

    // ---- MainView -------------------------------------------------------------------------------

    @Override
    public void showFolders(List<Account> accounts, Map<String, AccountState> states) {
        this.accounts = List.copyOf(accounts);
        folders.update(accounts, new HashMap<>(states));
    }

    @Override
    public void announceNewMail(String account, int count, Runnable open) {
        // One notification for new mail: a later check replaces it instead of piling up.
        newMailNotification = Notifications.send(Notification.of(count == 1 ? tr("notify.new-mail.one") : tr("notify.new-mail.many", count))
                .body(Notification.escape(account))
                .icon(getIconImage())
                .category("email.arrived")
                .replacing(newMailNotification)
                .defaultAction(() -> SwingUtilities.invokeLater(() -> {
                    open.run();
                    if (getState() == Frame.ICONIFIED) setState(Frame.NORMAL);
                    toFront();
                })));
    }

    @Override
    public void selectFirstInbox() {
        folders.selectFirstInbox();
    }

    @Override
    public void showMessages(List<MessageSummary> messages, boolean unified) {
        list.setMessages(messages, unified);
    }

    @Override
    public void updateMessage(MessageSummary message) {
        list.update(message);
    }

    @Override
    public void removeMessages(Collection<MessageSummary> messages) {
        list.remove(messages);
    }

    @Override
    public MessageSummary selectedMessage() {
        return list.selectedMessage();
    }

    @Override
    public List<MessageSummary> selectedMessages() {
        return list.selectedMessages();
    }

    @Override
    public void selectRelative(int delta) {
        list.selectRelative(delta);
    }

    @Override
    public void showMessage(DisplayedMessage message) {
        displayed = message;
        messageView.show(message);
    }

    @Override
    public void clearMessage(String text) {
        displayed = null;
        messageView.clear(text);
    }

    @Override
    public void showText(String text) {
        messageView.showText(text);
    }

    @Override
    public void setStatus(String text) {
        status.setText(text == null || text.isBlank() ? " " : text);
    }

    @Override
    public void setContext(String title) {
        setTitle(title == null || title.isBlank() ? tr("app.title") : tr("app.title.with", title));
    }

    @Override
    public void setShowHtml(boolean html) {
        if (menuBar != null) menuBar.setShowHtml(html);
    }

    @Override
    public void showError(String message) {
        JOptionPane.showMessageDialog(this, message, tr("app.title"), JOptionPane.ERROR_MESSAGE);
    }

    @Override
    public boolean confirm(String message) {
        return JOptionPane.showConfirmDialog(this, message, tr("app.title"), JOptionPane.OK_CANCEL_OPTION)
                == JOptionPane.OK_OPTION;
    }

    @Override
    public void openOptions(List<Account> accounts, MailPreferences preferences, String page, boolean addAccount) {
        OptionsDialog dialog = new OptionsDialog(this, presenter::testConnection, presenter::applyOptions, locales);
        dialog.open(accounts, preferences, page, addAccount);
    }

    @Override
    public void openCompose(List<Account> accounts, Draft draft) {
        ComposeWindow window = new ComposeWindow(accounts, draft, presenter::send, contacts, locales.locale());
        window.setIconImage(getIconImage());
        window.setVisible(true);
        window.focusBody();
    }

    @Override
    public Path chooseSaveFile(String suggestedName) {
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File(chooser.getCurrentDirectory(), suggestedName));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return null;
        File file = chooser.getSelectedFile();
        if (file.exists() && !confirm(tr("file.replace", file.getName()))) return null;
        return file.toPath();
    }

    @Override
    public boolean hasOpenComposeWindows() {
        for (Window w : Window.getWindows()) if (w instanceof ComposeWindow && w.isDisplayable()) return true;
        return false;
    }

    @Override
    public void dispose() {
        for (Window w : Window.getWindows()) if (w != this && w.isDisplayable()) w.dispose();
        super.dispose();
    }

    // ---- menu actions handled by the window -----------------------------------------------------

    void copySelection() {
        String selected = messageView.textArea().getSelectedText();
        if (selected != null) {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(selected), null);
        }
    }

    void selectAllMessages() {
        list.table().selectAll();
    }

    void saveAllAttachments() {
        if (displayed == null || displayed.content().attachments().isEmpty()) {
            setStatus(tr("attachments.none"));
            return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        Path directory = chooser.getSelectedFile().toPath();
        int saved = 0;
        for (Attachment attachment : displayed.content().attachments()) {
            Path target = directory.resolve(MainPresenter.safeFileName(attachment.fileName()));
            try {
                if (java.nio.file.Files.exists(target)) continue;
                java.nio.file.Files.write(target, attachment.data());
                saved++;
            } catch (java.io.IOException e) {
                showError(tr("attachments.error.save", target.toString(), e.getMessage()));
                return;
            }
        }
        setStatus(tr("attachments.saved", saved, directory.toString()));
    }

    void setFoldersVisible(boolean visible) {
        if (!visible) dividerLocation = horizontal.getDividerLocation();
        folders.setVisible(visible);
        if (visible) horizontal.setDividerLocation(dividerLocation);
        horizontal.revalidate();
    }

    void showAbout() {
        JOptionPane.showMessageDialog(this, tr("about.text") + "\nhttps://github.com/amadeos-eu/amade-mail",
                tr("menu.help.about"), JOptionPane.INFORMATION_MESSAGE, new ImageIcon(getIconImage()));
    }
}
