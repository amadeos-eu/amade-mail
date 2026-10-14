package org.datazuul.amadeos.mail.ui.main;

import org.datazuul.amadeos.mail.domain.Account;
import org.datazuul.amadeos.mail.domain.AccountColor;
import org.datazuul.amadeos.mail.domain.FolderRole;
import org.datazuul.amadeos.mail.domain.MailFolder;
import org.datazuul.amadeos.mail.domain.MailPreferences;
import org.datazuul.amadeos.mail.domain.MessageContent;
import org.datazuul.amadeos.mail.domain.MessageSummary;
import org.datazuul.amadeos.mail.repository.AccountRepository;
import org.datazuul.amadeos.commons.desktop.PasswordStore;
import org.datazuul.amadeos.mail.repository.PreferencesRepository;
import org.datazuul.amadeos.mail.service.CredentialProvider;
import org.datazuul.amadeos.mail.service.HtmlSanitizer;
import org.datazuul.amadeos.mail.service.MailGateway;
import org.datazuul.amadeos.mail.service.RemoteImageLoader;
import org.datazuul.amadeos.commons.desktop.DesktopLauncher;
import org.datazuul.amadeos.commons.swing.EdtExecutor;
import org.datazuul.amadeos.mail.ui.folders.TreeItem;
import org.datazuul.amadeos.mail.ui.view.DisplayedMessage;
import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Runs the presenter on the EDT with a same-thread executor, so every asynchronous step completes
 * before the action returns.
 */
class MainPresenterTest {
    private final MainView view = mock(MainView.class);
    private final MailGateway gateway = mock(MailGateway.class);
    private final AccountRepository accountRepository = mock(AccountRepository.class);
    private final PreferencesRepository preferencesRepository = mock(PreferencesRepository.class);
    private final RemoteImageLoader remoteImages = mock(RemoteImageLoader.class);
    private final Account account = new Account("a1", "Privat", "Ralf", "ralf@example.org", "ralf",
            Account.blank(AccountColor.BLUE).imap(), Account.blank(AccountColor.BLUE).smtp(), "", AccountColor.BLUE);
    private final MailFolder inbox = folder("INBOX", FolderRole.INBOX, 1);
    private final MailFolder trash = folder("Trash", FolderRole.TRASH, 0);
    private final MainPresenter presenter = new MainPresenter(view, gateway, accountRepository, preferencesRepository,
            mock(PasswordStore.class), mock(CredentialProvider.class), new HtmlSanitizer(), remoteImages,
            mock(DesktopLauncher.class), new DirectExecutorService(), new EdtExecutor());

    private static MailFolder folder(String name, FolderRole role, int unread) {
        return new MailFolder("a1", name, name, '/', role, true, unread, 10);
    }

    private static MessageSummary summary(long uid, boolean seen) {
        return new MessageSummary("a1", "INBOX", uid, "Bob", "bob@example.org", "Hi", Instant.now(), 100, seen,
                false, false, false);
    }

    private static void onEdt(Runnable action) throws Exception {
        SwingUtilities.invokeAndWait(action);
    }

    private void startWith(List<MailFolder> folders) throws Exception {
        when(accountRepository.load()).thenReturn(List.of(account));
        when(preferencesRepository.load()).thenReturn(MailPreferences.DEFAULTS);
        when(gateway.listFolders(account)).thenReturn(folders);
        onEdt(() -> presenter.start(List.of()));
    }

    @Test
    void startListsFoldersAndSelectsInbox() throws Exception {
        startWith(List.of(inbox, trash));
        verify(gateway).listFolders(account);
        verify(view).selectFirstInbox();
    }

    @Test
    void firstStartWithoutAccountsOpensAccountOptions() throws Exception {
        when(accountRepository.load()).thenReturn(List.of());
        when(preferencesRepository.load()).thenReturn(MailPreferences.DEFAULTS);
        onEdt(() -> presenter.start(List.of()));
        verify(view).openOptions(eq(List.of()), any(), eq("Accounts"), eq(true));
    }

    @Test
    void selectingAFolderLoadsItsMessages() throws Exception {
        startWith(List.of(inbox));
        List<MessageSummary> messages = List.of(summary(2, false), summary(1, true));
        when(gateway.listMessages(account, "INBOX", MainPresenter.MESSAGE_LIMIT)).thenReturn(messages);
        onEdt(() -> presenter.folderSelected(new TreeItem.FolderItem(account, inbox)));
        verify(view).showMessages(messages, false);
        verify(view).setStatus("Loaded 2 messages, 1 new");
    }

    @Test
    void statusMentionsTheTotalOnlyWhenTheListWasCutOff() {
        List<MessageSummary> two = List.of(summary(2, false), summary(1, true));
        assertEquals("Loaded 2 messages, 1 new", MainPresenter.loadedStatus(two, 10));
        List<MessageSummary> full = java.util.stream.LongStream.rangeClosed(1, MainPresenter.MESSAGE_LIMIT)
                .mapToObj(uid -> summary(uid, true)).toList();
        assertEquals("Loaded 500 messages, 0 new (newest 500 of 1,200)", MainPresenter.loadedStatus(full, 1200));
    }

    @Test
    void deleteMovesToTrash() throws Exception {
        startWith(List.of(inbox, trash));
        MessageSummary message = summary(7, true);
        when(view.selectedMessages()).thenReturn(List.of(message));
        onEdt(presenter::deleteSelected);
        verify(view, never()).confirm(anyString());
        verify(view).removeMessages(List.of(message));
        verify(gateway).move(account, "INBOX", List.of(7L), "Trash");
    }

    @Test
    void deleteWithoutTrashAsksAndDeletesPermanently() throws Exception {
        startWith(List.of(inbox));
        MessageSummary message = summary(7, true);
        when(view.selectedMessages()).thenReturn(List.of(message));
        when(view.confirm(anyString())).thenReturn(false);
        onEdt(presenter::deleteSelected);
        verify(gateway, never()).deletePermanently(any(), anyString(), anyList());

        when(view.confirm(anyString())).thenReturn(true);
        onEdt(presenter::deleteSelected);
        verify(gateway).deletePermanently(account, "INBOX", List.of(7L));
    }

    @Test
    void openingAnUnreadMessageMarksItRead() throws Exception {
        startWith(List.of(inbox));
        MessageSummary message = summary(3, false);
        MessageContent content = new MessageContent("Bob", "bob@example.org", "ralf@example.org", "", "", "Hi",
                null, "", "Hello", null, List.of(), Map.of());
        when(gateway.loadMessage(account, "INBOX", 3)).thenReturn(content);
        onEdt(() -> presenter.messageSelected(message));
        verify(view).showMessage(any(DisplayedMessage.class));
        verify(view).updateMessage(message.withSeen(true));
    }

    @Test
    void htmlIsSanitisedAndRemoteImagesStayBlocked() {
        MessageContent content = new MessageContent("Bob", "bob@example.org", "ralf@example.org", "", "", "Hi",
                null, "", null, "<p>Hi<img src=\"https://t.example/p.gif\"><img src=\"cid:c1\"></p>", List.of(),
                Map.of("c1", new MessageContent.InlinePart("c1", "image/png", new byte[]{1})));
        DisplayedMessage shown = presenter.prepare(summary(1, true), content, false, false);
        assertTrue(shown.showsHtml(), "HTML-only messages are shown as HTML");
        assertEquals(1, shown.html().blockedRemote());
        assertTrue(shown.resources().containsKey("cid:c1"));
        assertEquals("Hi", shown.plainText());
        verify(remoteImages, never()).load(any());
    }

    @Test
    void attachmentNamesCannotEscapeTheDirectory() {
        assertEquals("_.._etc_passwd", MainPresenter.safeFileName("../etc/passwd"));
        assertEquals("attachment", MainPresenter.safeFileName(".."));
        assertEquals("_.bashrc", MainPresenter.safeFileName(".bashrc"));
    }

    /** Runs tasks in the calling thread. */
    private static final class DirectExecutorService extends AbstractExecutorService {
        private volatile boolean shutdown;

        @Override
        public void execute(Runnable command) {
            command.run();
        }

        @Override
        public void shutdown() {
            shutdown = true;
        }

        @Override
        public List<Runnable> shutdownNow() {
            shutdown = true;
            return List.of();
        }

        @Override
        public boolean isShutdown() {
            return shutdown;
        }

        @Override
        public boolean isTerminated() {
            return shutdown;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return true;
        }
    }
}
