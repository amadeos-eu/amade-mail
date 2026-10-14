package org.datazuul.amadeos.mail.ui.main;

import static org.datazuul.amadeos.mail.i18n.Texts.tr;

import com.google.inject.Inject;
import org.datazuul.amadeos.mail.domain.Account;
import org.datazuul.amadeos.mail.domain.FolderRole;
import org.datazuul.amadeos.mail.domain.MailFolder;
import org.datazuul.amadeos.mail.domain.MailPreferences;
import org.datazuul.amadeos.mail.domain.MessageContent;
import org.datazuul.amadeos.mail.domain.MessageContent.Attachment;
import org.datazuul.amadeos.mail.domain.MessageSummary;
import org.datazuul.amadeos.mail.domain.OutgoingMessage;
import org.datazuul.amadeos.mail.repository.AccountRepository;
import org.datazuul.amadeos.commons.desktop.PasswordStore;
import org.datazuul.amadeos.mail.repository.PreferencesRepository;
import org.datazuul.amadeos.mail.service.CredentialProvider;
import org.datazuul.amadeos.mail.service.HtmlSanitizer;
import org.datazuul.amadeos.mail.service.HtmlSanitizer.SanitizedHtml;
import org.datazuul.amadeos.mail.service.MailGateway;
import org.datazuul.amadeos.mail.service.MailServiceException;
import org.datazuul.amadeos.mail.service.MailtoLink;
import org.datazuul.amadeos.mail.service.MessageComposer;
import org.datazuul.amadeos.mail.service.RemoteImageLoader;
import org.datazuul.amadeos.mail.service.SentCopyFailedException;
import org.datazuul.amadeos.commons.desktop.DesktopLauncher;
import org.datazuul.amadeos.mail.ui.compose.ComposeWindow;
import org.datazuul.amadeos.mail.ui.compose.Draft;
import org.datazuul.amadeos.mail.ui.folders.AccountState;
import org.datazuul.amadeos.mail.ui.folders.TreeItem;
import org.datazuul.amadeos.mail.ui.options.OptionsDialog;
import org.datazuul.amadeos.mail.ui.view.DisplayedMessage;
import org.datazuul.amadeos.mail.ui.view.MessageViewActions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.Timer;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Behaviour of the mailer. Runs on the EDT; every server operation goes to the background executor
 * and comes back to the EDT. Results that arrive after the user moved on are dropped.
 */
public final class MainPresenter implements MessageViewActions {
    private static final Logger LOG = LoggerFactory.getLogger(MainPresenter.class);
    static final int MESSAGE_LIMIT = 500;
    static final int SOURCE_LIMIT = 2 * 1024 * 1024;

    private final MainView view;
    private final MailGateway gateway;
    private final AccountRepository accountRepository;
    private final PreferencesRepository preferencesRepository;
    private final PasswordStore passwordStore;
    private final CredentialProvider credentials;
    private final HtmlSanitizer sanitizer;
    private final RemoteImageLoader remoteImages;
    private final DesktopLauncher desktop;
    private final ExecutorService executor;
    private final Executor edt;

    private List<Account> accounts = List.of();
    private final Map<String, AccountState> states = new HashMap<>();
    private MailPreferences preferences = MailPreferences.DEFAULTS;
    private boolean showHtml;
    private TreeItem current;
    private DisplayedMessage displayed;
    private int listGeneration;
    private int messageGeneration;
    private boolean inboxSelected;
    private Timer checkTimer;
    private Path attachmentDirectory;

    @Inject
    public MainPresenter(MainView view, MailGateway gateway, AccountRepository accountRepository,
                         PreferencesRepository preferencesRepository, PasswordStore passwordStore,
                         CredentialProvider credentials, HtmlSanitizer sanitizer, RemoteImageLoader remoteImages,
                         DesktopLauncher desktop, ExecutorService executor,
                         org.datazuul.amadeos.commons.swing.EdtExecutor edt) {
        this.view = view;
        this.gateway = gateway;
        this.accountRepository = accountRepository;
        this.preferencesRepository = preferencesRepository;
        this.passwordStore = passwordStore;
        this.credentials = credentials;
        this.sanitizer = sanitizer;
        this.remoteImages = remoteImages;
        this.desktop = desktop;
        this.executor = executor;
        this.edt = edt;
    }

    // ---- start and accounts ---------------------------------------------------------------------

    public void start(List<String> arguments) {
        accounts = loadSafely(accountRepository::load, List.of(), "error.load.accounts");
        preferences = loadSafely(preferencesRepository::load, MailPreferences.DEFAULTS, "error.load.preferences");
        showHtml = preferences.preferHtml();
        view.setShowHtml(showHtml);
        accounts.forEach(a -> states.put(a.id(), AccountState.LOADING));
        view.showFolders(accounts, states);
        view.setContext("");
        scheduleCheck();
        if (accounts.isEmpty()) {
            view.setStatus(tr("status.no-account"));
            openOptions(OptionsDialog.ACCOUNTS, true);
        } else {
            refreshFolders(accounts);
        }
        for (String argument : arguments) {
            if (MailtoLink.isMailto(argument)) compose(MailtoLink.parse(argument));
        }
    }

    private <T> T loadSafely(Supplier<T> loader, T fallback, String what) {
        try {
            return loader.get();
        } catch (RuntimeException e) {
            LOG.error("Cannot load {}", what, e);
            view.showError(tr(what, e.getMessage()));
            return fallback;
        }
    }

    public List<Account> accounts() {
        return accounts;
    }

    Optional<Account> account(String id) {
        return accounts.stream().filter(a -> a.id().equals(id)).findFirst();
    }

    private AccountState state(String accountId) {
        return states.getOrDefault(accountId, AccountState.LOADING);
    }

    private void refreshFolders(List<Account> which) {
        for (Account account : which) {
            states.put(account.id(), state(account.id()).loading());
            async(() -> gateway.listFolders(account), folders -> {
                if (account(account.id()).isEmpty()) return; // removed meanwhile
                states.put(account.id(), AccountState.online(folders));
                view.showFolders(accounts, states);
                if (!inboxSelected) {
                    inboxSelected = true;
                    view.selectFirstInbox();
                }
            }, error -> {
                if (account(account.id()).isEmpty()) return;
                states.put(account.id(), state(account.id()).offline(error));
                view.showFolders(accounts, states);
                view.setStatus(error);
            });
        }
        view.showFolders(accounts, states);
    }

    public void openOptions(String page, boolean addAccount) {
        view.openOptions(accounts, preferences, page, addAccount);
    }

    /** OK or Apply in Mail Options. */
    public void applyOptions(List<Account> edited, Map<String, String> passwords, MailPreferences newPreferences) {
        Map<String, Account> old = new HashMap<>();
        accounts.forEach(a -> old.put(a.id(), a));
        List<Account> changed = new ArrayList<>();
        for (Account account : edited) {
            Account before = old.remove(account.id());
            boolean serverChanged = before == null || !before.imap().equals(account.imap())
                    || !before.username().equals(account.username()) || before.auth() != account.auth()
                    || passwords.containsKey(account.id());
            // Tokens are kept when switching to OAuth2, as "Test connection" may have just signed in.
            if (before != null && before.auth().isOAuth2() && !account.auth().isOAuth2()) {
                credentials.signOut(account.id());
            }
            if (serverChanged) {
                gateway.disconnect(account.id());
                changed.add(account);
            }
        }
        for (Account removed : old.values()) {
            gateway.disconnect(removed.id());
            passwordStore.delete(removed.id());
            credentials.signOut(removed.id());
            states.remove(removed.id());
        }
        passwords.forEach((id, password) -> {
            credentials.passwordChanged(id, password);
            edited.stream().filter(a -> a.id().equals(id)).findFirst()
                    .ifPresent(a -> executor.execute(() -> passwordStore.store(id, "amade-mail " + a.email(), password)));
        });
        try {
            accountRepository.save(edited);
            preferencesRepository.save(newPreferences);
        } catch (RuntimeException e) {
            view.showError(tr("error.save.settings", e.getMessage()));
        }
        accounts = List.copyOf(edited);
        if (newPreferences.preferHtml() != preferences.preferHtml()) setShowHtml(newPreferences.preferHtml());
        preferences = newPreferences;
        scheduleCheck();
        refreshFolders(changed.stream().filter(Account::isComplete).toList());
        if (current instanceof TreeItem.FolderItem f && account(f.account().id()).isEmpty()) {
            current = null;
            view.showMessages(List.of(), false);
            view.clearMessage("");
        }
    }

    /** Test connection in Mail Options. */
    public void testConnection(Account account, String passwordOrNull, Consumer<String> result) {
        async(() -> {
            if (account.auth().isOAuth2()) {
                // Signs in in the web browser if no valid token is stored.
                String token = credentials.password(account);
                try {
                    gateway.testConnection(account, token);
                } catch (MailServiceException e) {
                    if (e.isAuthenticationFailure()) credentials.rejected(account);
                    throw e;
                }
                return tr("test.ok.oauth", account.auth().provider().label());
            }
            String password = passwordOrNull != null ? passwordOrNull : passwordStore.lookup(account.id())
                    .orElseThrow(() -> new MailServiceException(tr("test.no-password")));
            gateway.testConnection(account, password);
            return tr("test.ok");
        }, result, result);
    }

    // ---- folders and message list ---------------------------------------------------------------

    public void folderSelected(TreeItem item) {
        current = item;
        displayed = null;
        int generation = ++listGeneration;
        ++messageGeneration;
        view.clearMessage("");
        if (item == null) return;
        switch (item) {
            case TreeItem.UnifiedInbox u -> {
                view.setContext(tr("folders.all-inboxes"));
                loadUnified(generation);
            }
            case TreeItem.FolderItem f when f.folder().selectable() -> {
                view.setContext(f.account().label() + " - " + f.folder().fullName());
                loadFolder(generation, f.account(), f.folder());
            }
            case TreeItem.FolderItem f -> {
                view.setContext(f.account().label() + " - " + f.folder().fullName());
                view.showMessages(List.of(), false);
                view.setStatus(tr("status.folders-only"));
            }
            case TreeItem.AccountItem a -> {
                view.setContext(a.account().label());
                view.showMessages(List.of(), false);
                view.setStatus(a.state().error().isEmpty() ? a.account().email() : a.state().error());
            }
        }
    }

    private void loadFolder(int generation, Account account, MailFolder folder) {
        view.setStatus(tr("status.loading", folder.name()));
        async(() -> gateway.listMessages(account, folder.fullName(), MESSAGE_LIMIT), messages -> {
            if (generation != listGeneration) return;
            view.showMessages(messages, false);
            view.setStatus(loadedStatus(messages, folder.total()));
        }, error -> {
            if (generation != listGeneration) return;
            view.showMessages(List.of(), false);
            view.setStatus(error);
        });
    }

    private void loadUnified(int generation) {
        view.setStatus(tr("status.loading-all"));
        Map<Account, CompletableFuture<List<MessageSummary>>> futures = new LinkedHashMap<>();
        for (Account account : accounts) {
            String inbox = state(account.id()).folder(FolderRole.INBOX).map(MailFolder::fullName).orElse("INBOX");
            futures.put(account, CompletableFuture.supplyAsync(
                    () -> gateway.listMessages(account, inbox, MESSAGE_LIMIT), executor));
        }
        CompletableFuture.allOf(futures.values().stream()
                        .map(f -> f.exceptionally(e -> List.of()))
                        .toArray(CompletableFuture[]::new))
                .whenCompleteAsync((ignored, e) -> {
                    if (generation != listGeneration) return;
                    List<MessageSummary> merged = new ArrayList<>();
                    List<String> errors = new ArrayList<>();
                    futures.forEach((account, future) -> {
                        try {
                            merged.addAll(future.get());
                        } catch (InterruptedException | ExecutionException ex) {
                            errors.add(account.label() + ": " + message(ex));
                        }
                    });
                    merged.sort(Comparator.comparing(MessageSummary::date,
                            Comparator.nullsLast(Comparator.reverseOrder())));
                    view.showMessages(merged, true);
                    view.setStatus(loadedStatus(merged, -1) + (errors.isEmpty() ? "" : " — " + String.join("; ", errors)));
                }, edt);
    }

    /**
     * @param total message count from the folder tree; it may be stale, so it is only mentioned when the
     *              list was actually cut off at {@link #MESSAGE_LIMIT}
     */
    static String loadedStatus(List<MessageSummary> messages, int total) {
        long unread = messages.stream().filter(m -> !m.seen()).count();
        String text = tr("status.loaded", messages.size(), unread);
        boolean truncated = messages.size() >= MESSAGE_LIMIT && total > messages.size();
        return truncated ? text + " " + tr("status.newest", messages.size(), total) : text;
    }

    public void checkMail() {
        refreshFolders(accounts.stream().filter(Account::isComplete).toList());
        if (current != null) {
            int generation = ++listGeneration;
            switch (current) {
                case TreeItem.UnifiedInbox u -> loadUnified(generation);
                case TreeItem.FolderItem f when f.folder().selectable() -> loadFolder(generation, f.account(), f.folder());
                default -> {
                }
            }
        }
    }

    private void scheduleCheck() {
        if (checkTimer != null) checkTimer.stop();
        int minutes = preferences.checkIntervalMinutes();
        if (minutes <= 0) return;
        checkTimer = new Timer(minutes * 60_000, e -> checkMail());
        checkTimer.setRepeats(true);
        checkTimer.start();
    }

    // ---- message view ---------------------------------------------------------------------------

    public void messageSelected(MessageSummary message) {
        int generation = ++messageGeneration;
        displayed = null;
        if (message == null) {
            view.clearMessage("");
            return;
        }
        Optional<Account> account = account(message.accountId());
        if (account.isEmpty()) return;
        boolean html = showHtml;
        async(() -> {
            MessageContent content = gateway.loadMessage(account.get(), message.folder(), message.uid());
            return prepare(message, content, html, false);
        }, prepared -> {
            if (generation != messageGeneration) return;
            displayed = prepared;
            view.showMessage(prepared);
            if (!message.seen()) {
                view.updateMessage(message.withSeen(true));
                adjustUnread(message, -1);
            }
        }, error -> {
            if (generation == messageGeneration) view.clearMessage(error);
        });
    }

    /** Runs in the background: sanitises the HTML and downloads remote images if allowed. */
    DisplayedMessage prepare(MessageSummary summary, MessageContent content, boolean preferHtml, boolean forceRemote) {
        String text = content.hasPlainText() ? content.plainText()
                : content.hasHtml() ? sanitizer.toPlainText(content.html()) : "";
        boolean html = content.hasHtml() && (preferHtml || !content.hasPlainText());
        if (!html) return new DisplayedMessage(summary, content, text, null, Map.of());
        boolean allowRemote = forceRemote || preferences.loadsRemoteImagesFrom(content.senderAddress());
        SanitizedHtml sanitized = sanitizer.sanitize(content.html(), allowRemote);
        Map<String, byte[]> resources = new HashMap<>();
        content.inlineParts().forEach((id, part) -> resources.put("cid:" + id, part.data()));
        if (allowRemote) resources.putAll(remoteImages.load(RemoteImageLoader.imageUrls(sanitized.document())));
        return new DisplayedMessage(summary, content, text, sanitized, resources);
    }

    public void setShowHtml(boolean html) {
        showHtml = html;
        view.setShowHtml(html);
        redisplay(false);
    }

    private void redisplay(boolean forceRemote) {
        DisplayedMessage shown = displayed;
        if (shown == null) return;
        int generation = messageGeneration;
        boolean html = showHtml;
        async(() -> prepare(shown.summary(), shown.content(), html, forceRemote), prepared -> {
            if (generation != messageGeneration) return;
            displayed = prepared;
            view.showMessage(prepared);
        }, view::setStatus);
    }

    @Override
    public void loadRemoteImagesOnce() {
        view.setStatus(tr("status.loading-images"));
        redisplay(true);
    }

    @Override
    public void alwaysLoadRemoteImagesFromSender() {
        if (displayed == null || displayed.content().senderAddress().isBlank()) return;
        preferences = preferences.withTrustedSender(displayed.content().senderAddress());
        try {
            preferencesRepository.save(preferences);
        } catch (RuntimeException e) {
            view.showError(tr("error.save.preferences", e.getMessage()));
        }
        loadRemoteImagesOnce();
    }

    @Override
    public void linkClicked(String uri) {
        if (uri == null || uri.isBlank()) return;
        if (MailtoLink.isMailto(uri)) {
            compose(MailtoLink.parse(uri));
            return;
        }
        String lower = uri.toLowerCase(Locale.ROOT);
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            view.setStatus(tr("link.not-opened", uri));
            return;
        }
        if (!view.confirm(tr("link.confirm", uri))) return;
        try {
            desktop.browse(uri);
        } catch (IOException | RuntimeException e) {
            view.showError(tr("link.error", e.getMessage()));
        }
    }

    @Override
    public void openAttachment(Attachment attachment) {
        if (DesktopLauncher.isRisky(attachment.fileName())) {
            view.showError(tr("attachment.risky", attachment.fileName()));
            return;
        }
        async(() -> {
            Path file = writeTemporary(attachment);
            try {
                desktop.open(file);
            } catch (IOException e) {
                throw new MailServiceException(tr("attachment.error.open", file.getFileName().toString(), e.getMessage()), e);
            }
            return file;
        }, file -> view.setStatus(tr("attachment.opened", file.getFileName().toString())), view::showError);
    }

    private synchronized Path writeTemporary(Attachment attachment) {
        try {
            if (attachmentDirectory == null) attachmentDirectory = Files.createTempDirectory("amade-mail-");
            Path directory = Files.createTempDirectory(attachmentDirectory, "a");
            Path file = directory.resolve(safeFileName(attachment.fileName()));
            Files.write(file, attachment.data());
            file.toFile().deleteOnExit();
            directory.toFile().deleteOnExit();
            return file;
        } catch (IOException e) {
            throw new MailServiceException(tr("attachment.error.write", e.getMessage()), e);
        }
    }

    static String safeFileName(String name) {
        String cleaned = name.replaceAll("[/\\\\\\x00-\\x1f]", "_").strip();
        if (cleaned.isEmpty() || cleaned.equals(".") || cleaned.equals("..")) return "attachment";
        return cleaned.startsWith(".") ? "_" + cleaned : cleaned;
    }

    @Override
    public void saveAttachment(Attachment attachment) {
        Path target = view.chooseSaveFile(safeFileName(attachment.fileName()));
        if (target == null) return;
        async(() -> {
            try {
                Files.write(target, attachment.data());
                return target;
            } catch (IOException e) {
                throw new MailServiceException(tr("attachments.error.save", target.toString(), e.getMessage()), e);
            }
        }, file -> view.setStatus(tr("attachment.saved", file.toString())), view::showError);
    }

    public void showSource() {
        MessageSummary message = view.selectedMessage();
        if (message == null) return;
        account(message.accountId()).ifPresent(account -> {
            int generation = messageGeneration;
            async(() -> gateway.loadSource(account, message.folder(), message.uid(), SOURCE_LIMIT), source -> {
                if (generation == messageGeneration) view.showText(source);
            }, view::showError);
        });
    }

    // ---- message actions ------------------------------------------------------------------------

    public void next() {
        view.selectRelative(1);
    }

    public void previous() {
        view.selectRelative(-1);
    }

    /** Moves the selection to Trash, or deletes it for good inside Trash (after asking). */
    public void deleteSelected() {
        List<MessageSummary> selected = view.selectedMessages();
        if (selected.isEmpty()) return;
        Map<String, List<MessageSummary>> groups = groupByFolder(selected);
        boolean permanent = groups.values().stream().anyMatch(g -> trashFor(g.getFirst()).isEmpty());
        if (permanent && !view.confirm(tr("delete.confirm", selected.size()))) return;
        view.removeMessages(selected);
        for (List<MessageSummary> group : groups.values()) {
            MessageSummary first = group.getFirst();
            Account account = account(first.accountId()).orElse(null);
            if (account == null) continue;
            List<Long> uids = group.stream().map(MessageSummary::uid).toList();
            Optional<MailFolder> trash = trashFor(first);
            async(() -> {
                if (trash.isPresent()) gateway.move(account, first.folder(), uids, trash.get().fullName());
                else gateway.deletePermanently(account, first.folder(), uids);
                return null;
            }, ignored -> refreshCounts(account, first.folder(), trash.map(MailFolder::fullName).orElse(null)),
                    this::operationFailed);
        }
    }

    /** The Trash folder of the message's account, unless the message already is in Trash. */
    private Optional<MailFolder> trashFor(MessageSummary message) {
        return state(message.accountId()).folder(FolderRole.TRASH)
                .filter(trash -> !trash.fullName().equals(message.folder()));
    }

    /** Folders the selected messages can be moved to (Move menu). */
    public List<MailFolder> moveTargets() {
        MessageSummary first = view.selectedMessage();
        if (first == null) return List.of();
        return state(first.accountId()).folders().stream()
                .filter(MailFolder::selectable)
                .filter(f -> !f.fullName().equals(first.folder()))
                .sorted(org.datazuul.amadeos.mail.ui.folders.FolderTreeBuilder.ORDER)
                .toList();
    }

    public void moveSelected(MailFolder target) {
        List<MessageSummary> selected = view.selectedMessages().stream()
                .filter(m -> m.accountId().equals(target.accountId()))
                .toList();
        if (selected.isEmpty()) return;
        Account account = account(target.accountId()).orElse(null);
        if (account == null) return;
        view.removeMessages(selected);
        for (List<MessageSummary> group : groupByFolder(selected).values()) {
            String from = group.getFirst().folder();
            List<Long> uids = group.stream().map(MessageSummary::uid).toList();
            async(() -> {
                gateway.move(account, from, uids, target.fullName());
                return null;
            }, ignored -> {
                view.setStatus(tr("move.done", uids.size(), target.name()));
                refreshCounts(account, from, target.fullName());
            }, this::operationFailed);
        }
    }

    public void markUnread() {
        List<MessageSummary> selected = view.selectedMessages().stream().filter(MessageSummary::seen).toList();
        for (List<MessageSummary> group : groupByFolder(selected).values()) {
            MessageSummary first = group.getFirst();
            Account account = account(first.accountId()).orElse(null);
            if (account == null) continue;
            List<Long> uids = group.stream().map(MessageSummary::uid).toList();
            async(() -> {
                gateway.setSeen(account, first.folder(), uids, false);
                return null;
            }, ignored -> group.forEach(m -> {
                view.updateMessage(m.withSeen(false));
                adjustUnread(m, 1);
            }), this::operationFailed);
        }
    }

    private void operationFailed(String error) {
        view.showError(error);
        checkMail(); // bring the list back in line with the server
    }

    private static Map<String, List<MessageSummary>> groupByFolder(List<MessageSummary> messages) {
        Map<String, List<MessageSummary>> groups = new LinkedHashMap<>();
        for (MessageSummary m : messages) {
            groups.computeIfAbsent(m.accountId() + "\n" + m.folder(), k -> new ArrayList<>()).add(m);
        }
        return groups;
    }

    private void refreshCounts(Account account, String... folderNames) {
        Set<String> names = new HashSet<>();
        for (String name : folderNames) if (name != null) names.add(name);
        for (String name : names) {
            Optional<MailFolder> folder = state(account.id()).folder(name);
            if (folder.isEmpty()) continue;
            async(() -> gateway.refreshCounts(account, folder.get()), updated -> {
                states.put(account.id(), state(account.id()).withFolder(updated));
                view.showFolders(accounts, states);
            }, error -> LOG.debug("Count refresh failed: {}", error));
        }
    }

    private void adjustUnread(MessageSummary message, int delta) {
        AccountState state = state(message.accountId());
        state.folder(message.folder()).ifPresent(folder -> {
            int unread = folder.unread() < 0 ? folder.unread() : Math.max(0, folder.unread() + delta);
            states.put(message.accountId(), state.withFolder(folder.withCounts(unread, folder.total())));
            view.showFolders(accounts, states);
        });
    }

    // ---- compose --------------------------------------------------------------------------------

    /** The account of the selected folder, otherwise the first one. */
    private Optional<Account> currentAccount() {
        if (current instanceof TreeItem.FolderItem f) return account(f.account().id());
        if (current instanceof TreeItem.AccountItem a) return account(a.account().id());
        MessageSummary selected = view.selectedMessage();
        if (selected != null) return account(selected.accountId());
        return accounts.stream().findFirst();
    }

    public void composeNew() {
        Optional<Account> account = currentAccount();
        if (account.isEmpty()) {
            view.showError(tr("compose.no-account"));
            return;
        }
        view.openCompose(accounts, Draft.empty(account.get().id(), MessageComposer.newBody(account.get().signature())));
    }

    private void compose(MailtoLink link) {
        Optional<Account> account = currentAccount();
        if (account.isEmpty()) {
            view.showError(tr("compose.no-account"));
            return;
        }
        String body = link.body() + MessageComposer.newBody(account.get().signature());
        view.openCompose(accounts, new Draft(account.get().id(), link.to(), link.cc(), link.subject(), body, ""));
    }

    public void reply(boolean all) {
        DisplayedMessage shown = displayedSelection();
        if (shown == null) return;
        MessageContent original = shown.content();
        Account account = account(shown.summary().accountId()).orElseThrow();
        String cc = "";
        if (all) {
            List<String> others = new ArrayList<>();
            for (String part : (original.to() + "," + original.cc()).split(",")) {
                String address = part.strip();
                if (!address.isEmpty() && !address.toLowerCase(Locale.ROOT).contains(account.email().toLowerCase(Locale.ROOT))) {
                    others.add(address);
                }
            }
            cc = String.join(", ", others);
        }
        view.openCompose(accounts, new Draft(account.id(), MessageComposer.replyRecipient(original), cc,
                MessageComposer.replySubject(original.subject()),
                MessageComposer.quote(original, shown.plainText(), account.signature()),
                original.messageId()));
    }

    public void forward() {
        DisplayedMessage shown = displayedSelection();
        if (shown == null) return;
        MessageContent original = shown.content();
        Account account = account(shown.summary().accountId()).orElseThrow();
        view.openCompose(accounts, new Draft(account.id(), "", "", MessageComposer.forwardSubject(original.subject()),
                MessageComposer.forwardBody(original, shown.plainText(), account.signature()), ""));
    }

    private DisplayedMessage displayedSelection() {
        MessageSummary selected = view.selectedMessage();
        if (displayed == null || selected == null || !displayed.summary().sameMessage(selected)) {
            view.setStatus(tr("status.select-message"));
            return null;
        }
        return displayed;
    }

    /** Send button of a compose window. */
    public void send(OutgoingMessage message, ComposeWindow window) {
        String sent = state(message.account().id()).folder(FolderRole.SENT).map(MailFolder::fullName).orElse(null);
        async(() -> {
            gateway.send(message, sent);
            return null;
        }, ignored -> {
            window.sent();
            view.setStatus(tr("status.sent"));
            if (sent != null) refreshCounts(message.account(), sent);
        }, error -> window.failed(error), SentCopyFailedException.class, e -> {
            window.sent();
            view.showError(e.getMessage());
        });
    }

    // ---- exit -----------------------------------------------------------------------------------

    public void exit() {
        if (view.hasOpenComposeWindows() && !view.confirm(tr("exit.discard"))) return;
        if (checkTimer != null) checkTimer.stop();
        view.dispose();
        executor.execute(gateway::close);
        executor.shutdown();
    }

    // ---- async helpers --------------------------------------------------------------------------

    private <T> void async(Supplier<T> task, Consumer<T> onSuccess, Consumer<String> onError) {
        async(task, onSuccess, onError, null, null);
    }

    private <T, E extends Throwable> void async(Supplier<T> task, Consumer<T> onSuccess, Consumer<String> onError,
                                                Class<E> special, Consumer<E> onSpecial) {
        CompletableFuture.supplyAsync(task, executor).whenCompleteAsync((result, failure) -> {
            if (failure == null) {
                onSuccess.accept(result);
                return;
            }
            Throwable cause = unwrap(failure);
            if (special != null && special.isInstance(cause)) {
                onSpecial.accept(special.cast(cause));
                return;
            }
            if (!(cause instanceof MailServiceException)) LOG.error("Unexpected failure", cause);
            onError.accept(message(cause));
        }, edt);
    }

    private static Throwable unwrap(Throwable t) {
        while ((t instanceof CompletionException || t instanceof ExecutionException) && t.getCause() != null) {
            t = t.getCause();
        }
        return t;
    }

    static String message(Throwable t) {
        Throwable cause = unwrap(t);
        if (cause instanceof MailServiceException) return cause.getMessage();
        String text = cause.getMessage();
        return cause.getClass().getSimpleName() + (text == null ? "" : ": " + text);
    }
}
