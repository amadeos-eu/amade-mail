package org.datazuul.amadeos.mail.service;

import static org.datazuul.amadeos.mail.i18n.Texts.tr;

import com.google.inject.Inject;
import jakarta.mail.AuthenticationFailedException;
import jakarta.mail.FetchProfile;
import jakarta.mail.Flags;
import jakarta.mail.Folder;
import jakarta.mail.FolderClosedException;
import jakarta.mail.FolderNotFoundException;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.StoreClosedException;
import jakarta.mail.Transport;
import jakarta.mail.UIDFolder;
import jakarta.mail.internet.MimeMessage;
import org.datazuul.amadeos.mail.domain.Account;
import org.datazuul.amadeos.mail.domain.AuthMethod;
import org.datazuul.amadeos.mail.domain.FolderRole;
import org.datazuul.amadeos.mail.domain.MailFolder;
import org.datazuul.amadeos.mail.domain.MessageContent;
import org.datazuul.amadeos.mail.domain.MessageSummary;
import org.datazuul.amadeos.mail.domain.OutgoingMessage;
import org.datazuul.amadeos.mail.domain.ServerSettings;
import org.eclipse.angus.mail.imap.IMAPFolder;
import org.eclipse.angus.mail.imap.IMAPStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * {@link MailGateway} on Jakarta Mail (Eclipse Angus). Keeps one IMAP connection per account;
 * operations on the same account are serialised, different accounts run in parallel, so a slow
 * server never blocks the others.
 */
public final class JakartaMailGateway implements MailGateway {
    private static final Logger LOG = LoggerFactory.getLogger(JakartaMailGateway.class);

    private final CredentialProvider credentials;
    private final MessageParser parser;
    private final MessageComposer composer;
    private final Map<String, Connection> connections = new ConcurrentHashMap<>();

    @Inject
    public JakartaMailGateway(CredentialProvider credentials, MessageParser parser, MessageComposer composer) {
        this.credentials = credentials;
        this.parser = parser;
        this.composer = composer;
    }

    // ---- connection handling --------------------------------------------------------------------

    @FunctionalInterface
    private interface StoreAction<T> {
        T run(Store store) throws MessagingException, IOException;
    }

    private static final class Connection {
        final ReentrantLock lock = new ReentrantLock();
        Store store;
        ServerSettings settings;
        String username;
        AuthMethod auth;
    }

    private <T> T withStore(Account account, StoreAction<T> action) {
        Connection connection = connections.computeIfAbsent(account.id(), id -> new Connection());
        connection.lock.lock();
        try {
            for (int attempt = 0; ; attempt++) {
                try {
                    return action.run(connect(connection, account));
                } catch (StoreClosedException | FolderClosedException e) {
                    // The server dropped an idle connection; reconnect once.
                    closeQuietly(connection);
                    if (attempt > 0) throw failure(account, e);
                } catch (AuthenticationFailedException e) {
                    closeQuietly(connection);
                    credentials.rejected(account);
                    throw failure(account, e);
                } catch (MessagingException | IOException e) {
                    if (connection.store != null && !connection.store.isConnected()) closeQuietly(connection);
                    throw failure(account, e);
                }
            }
        } finally {
            connection.lock.unlock();
        }
    }

    private Store connect(Connection connection, Account account) throws MessagingException {
        if (connection.store != null && connection.store.isConnected()
                && account.imap().equals(connection.settings) && account.username().equals(connection.username)
                && account.auth() == connection.auth) {
            return connection.store;
        }
        closeQuietly(connection);
        Store store = openStore(account, credentials.password(account));
        connection.store = store;
        connection.settings = account.imap();
        connection.username = account.username();
        connection.auth = account.auth();
        return store;
    }

    private static Store openStore(Account account, String password) throws MessagingException {
        ServerSettings imap = account.imap();
        Session session = Session.getInstance(MailSessions.imapProperties(imap, account.auth()));
        Store store = session.getStore(MailSessions.imapProtocol(imap));
        store.connect(imap.host(), imap.port(), account.username(), password);
        return store;
    }

    private static void closeQuietly(Connection connection) {
        if (connection.store != null) {
            try {
                connection.store.close();
            } catch (MessagingException e) {
                LOG.debug("Closing store failed", e);
            }
        }
        connection.store = null;
        connection.settings = null;
        connection.username = null;
        connection.auth = null;
    }

    @Override
    public void disconnect(String accountId) {
        Connection connection = connections.remove(accountId);
        if (connection == null) return;
        connection.lock.lock();
        try {
            closeQuietly(connection);
        } finally {
            connection.lock.unlock();
        }
    }

    @Override
    public void close() {
        for (String id : List.copyOf(connections.keySet())) disconnect(id);
    }

    // ---- operations -----------------------------------------------------------------------------

    @Override
    public void testConnection(Account account, String password) {
        try {
            Store store = openStore(account, password);
            store.close();
        } catch (MessagingException e) {
            throw failure("IMAP", account.imap(), account.auth(), e);
        }
        try {
            ServerSettings smtp = account.smtp();
            Session session = Session.getInstance(MailSessions.smtpProperties(smtp, account.auth()));
            try (Transport transport = session.getTransport(MailSessions.smtpProtocol(smtp))) {
                transport.connect(smtp.host(), smtp.port(), account.username(), password);
            }
        } catch (MessagingException e) {
            throw failure("SMTP", account.smtp(), account.auth(), e);
        }
    }

    @Override
    public List<MailFolder> listFolders(Account account) {
        return withStore(account, store -> {
            List<MailFolder> result = new ArrayList<>();
            boolean hasInbox = false;
            for (Folder folder : store.getDefaultFolder().list("*")) {
                MailFolder mailFolder = describe(account, folder, true);
                hasInbox |= mailFolder.role() == FolderRole.INBOX;
                result.add(mailFolder);
            }
            if (!hasInbox) result.add(describe(account, store.getFolder("INBOX"), true));
            return result;
        });
    }

    @Override
    public MailFolder refreshCounts(Account account, MailFolder folder) {
        return withStore(account, store -> {
            Folder f = store.getFolder(folder.fullName());
            return folder.withCounts(count(f, true), count(f, false));
        });
    }

    private static MailFolder describe(Account account, Folder folder, boolean withCounts) throws MessagingException {
        String[] attributes = folder instanceof IMAPFolder imap ? imap.getAttributes() : new String[0];
        if (attributes == null) attributes = new String[0];
        boolean noSelect = Arrays.stream(attributes)
                .anyMatch(a -> a.equalsIgnoreCase("\\Noselect") || a.equalsIgnoreCase("\\NonExistent"));
        boolean selectable = !noSelect && (folder.getType() & Folder.HOLDS_MESSAGES) != 0;
        String fullName = folder.getFullName();
        FolderRole role = FolderRole.detect(fullName, folder.getName(), attributes);
        int unread = selectable && withCounts ? count(folder, true) : -1;
        int total = selectable && withCounts ? count(folder, false) : -1;
        return new MailFolder(account.id(), fullName, folder.getName(), folder.getSeparator(), role, selectable,
                unread, total);
    }

    /** IMAP answers these with STATUS without opening the folder. */
    private static int count(Folder folder, boolean unread) {
        try {
            return unread ? folder.getUnreadMessageCount() : folder.getMessageCount();
        } catch (MessagingException e) {
            LOG.debug("No counts for {}", folder.getFullName(), e);
            return -1;
        }
    }

    @Override
    public List<MessageSummary> listMessages(Account account, String folderName, int limit) {
        return withStore(account, store -> {
            Folder folder = open(store, folderName, Folder.READ_ONLY);
            try {
                int count = folder.getMessageCount();
                if (count <= 0) return List.of();
                Message[] messages = folder.getMessages(Math.max(1, count - limit + 1), count);
                FetchProfile profile = new FetchProfile();
                profile.add(FetchProfile.Item.ENVELOPE);
                profile.add(FetchProfile.Item.FLAGS);
                profile.add(FetchProfile.Item.CONTENT_INFO);
                profile.add(FetchProfile.Item.SIZE);
                profile.add(UIDFolder.FetchProfileItem.UID);
                folder.fetch(messages, profile);
                UIDFolder uidFolder = (UIDFolder) folder;
                List<MessageSummary> result = new ArrayList<>(messages.length);
                for (int i = messages.length - 1; i >= 0; i--) {
                    try {
                        result.add(summary(account, folderName, uidFolder.getUID(messages[i]), messages[i]));
                    } catch (MessagingException e) {
                        LOG.debug("Skipping unreadable message {} in {}", i, folderName, e);
                    }
                }
                return result;
            } finally {
                closeQuietly(folder);
            }
        });
    }

    private static MessageSummary summary(Account account, String folder, long uid, Message m) throws MessagingException {
        Date date = m.getSentDate() != null ? m.getSentDate() : m.getReceivedDate();
        Instant instant = date == null ? null : date.toInstant();
        return new MessageSummary(account.id(), folder, uid,
                MessageParser.displayName(m.getFrom()),
                MessageParser.firstAddress(m.getFrom()),
                m.getSubject(),
                instant,
                m.getSize(),
                m.isSet(Flags.Flag.SEEN),
                m.isSet(Flags.Flag.ANSWERED),
                m.isSet(Flags.Flag.FLAGGED),
                m.isMimeType("multipart/mixed"));
    }

    @Override
    public MessageContent loadMessage(Account account, String folderName, long uid) {
        return withStore(account, store -> {
            Folder folder = open(store, folderName, Folder.READ_WRITE);
            try {
                Message message = byUid(folder, uid);
                // The copy fetches the whole message in one round trip (and sets \Seen on the server).
                return parser.parse(new MimeMessage((MimeMessage) message));
            } finally {
                closeQuietly(folder);
            }
        });
    }

    @Override
    public String loadSource(Account account, String folderName, long uid, int maxBytes) {
        return withStore(account, store -> {
            Folder folder = open(store, folderName, Folder.READ_ONLY);
            try {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byUid(folder, uid).writeTo(out);
                byte[] bytes = out.toByteArray();
                if (bytes.length <= maxBytes) return new String(bytes, StandardCharsets.UTF_8);
                return new String(bytes, 0, maxBytes, StandardCharsets.UTF_8) + "\n[... truncated ...]";
            } finally {
                closeQuietly(folder);
            }
        });
    }

    @Override
    public void setSeen(Account account, String folderName, List<Long> uids, boolean seen) {
        withStore(account, store -> {
            Folder folder = open(store, folderName, Folder.READ_WRITE);
            try {
                Message[] messages = byUids(folder, uids);
                if (messages.length > 0) folder.setFlags(messages, new Flags(Flags.Flag.SEEN), seen);
                return null;
            } finally {
                closeQuietly(folder);
            }
        });
    }

    @Override
    public void move(Account account, String fromFolder, List<Long> uids, String toFolder) {
        withStore(account, store -> {
            Folder from = open(store, fromFolder, Folder.READ_WRITE);
            try {
                Message[] messages = byUids(from, uids);
                if (messages.length == 0) return null;
                Folder to = store.getFolder(toFolder);
                if (from instanceof IMAPFolder imap && hasCapability(store, "MOVE")) {
                    imap.moveMessages(messages, to);
                } else {
                    from.copyMessages(messages, to);
                    expunge(store, from, messages);
                }
                return null;
            } finally {
                closeQuietly(from);
            }
        });
    }

    @Override
    public void deletePermanently(Account account, String folderName, List<Long> uids) {
        withStore(account, store -> {
            Folder folder = open(store, folderName, Folder.READ_WRITE);
            try {
                Message[] messages = byUids(folder, uids);
                if (messages.length > 0) expunge(store, folder, messages);
                return null;
            } finally {
                closeQuietly(folder);
            }
        });
    }

    /** Removes exactly these messages; without UIDPLUS other \Deleted messages go as well. */
    private static void expunge(Store store, Folder folder, Message[] messages) throws MessagingException {
        folder.setFlags(messages, new Flags(Flags.Flag.DELETED), true);
        if (folder instanceof IMAPFolder imap && hasCapability(store, "UIDPLUS")) imap.expunge(messages);
        else folder.expunge();
    }

    @Override
    public void send(OutgoingMessage outgoing, String sentFolder) {
        Account account = outgoing.account();
        ServerSettings smtp = account.smtp();
        MimeMessage message;
        try {
            Session session = Session.getInstance(MailSessions.smtpProperties(smtp, account.auth()));
            message = composer.build(session, outgoing);
            try (Transport transport = session.getTransport(MailSessions.smtpProtocol(smtp))) {
                transport.connect(smtp.host(), smtp.port(), account.username(), credentials.password(account));
                transport.sendMessage(message, message.getAllRecipients());
            }
        } catch (AuthenticationFailedException e) {
            credentials.rejected(account);
            throw failure("SMTP", smtp, account.auth(), e);
        } catch (MessagingException e) {
            throw failure("SMTP", smtp, account.auth(), e);
        } catch (IOException e) {
            throw new MailServiceException(tr("mail.error.attachment", e.getMessage()), e);
        }
        if (sentFolder == null) return;
        try {
            withStore(account, store -> {
                message.setFlag(Flags.Flag.SEEN, true);
                store.getFolder(sentFolder).appendMessages(new Message[]{message});
                return null;
            });
        } catch (MailServiceException e) {
            throw new SentCopyFailedException(tr("mail.error.sent-copy", sentFolder, e.getMessage()), e);
        }
    }

    // ---- helpers ----------------------------------------------------------------------------------

    private static Folder open(Store store, String name, int mode) throws MessagingException {
        Folder folder = store.getFolder(name);
        try {
            folder.open(mode);
        } catch (FolderNotFoundException e) {
            throw new MailServiceException(tr("mail.error.folder-gone", name), e);
        }
        return folder;
    }

    private static Message byUid(Folder folder, long uid) throws MessagingException {
        Message message = ((UIDFolder) folder).getMessageByUID(uid);
        if (message == null) throw new MailServiceException(tr("mail.error.message-gone"));
        return message;
    }

    private static Message[] byUids(Folder folder, List<Long> uids) throws MessagingException {
        long[] array = uids.stream().mapToLong(Long::longValue).toArray();
        return Arrays.stream(((UIDFolder) folder).getMessagesByUID(array))
                .filter(Objects::nonNull)
                .toArray(Message[]::new);
    }

    private static boolean hasCapability(Store store, String capability) throws MessagingException {
        return store instanceof IMAPStore imap && imap.hasCapability(capability);
    }

    private static void closeQuietly(Folder folder) {
        try {
            if (folder.isOpen()) folder.close(false);
        } catch (MessagingException e) {
            LOG.debug("Closing folder failed", e);
        }
    }

    private static MailServiceException failure(Account account, Exception e) {
        if (e instanceof MailServiceException mse) return mse;
        return failure("IMAP", account.imap(), account.auth(), e);
    }

    static MailServiceException failure(String protocol, ServerSettings server, AuthMethod method, Exception e) {
        if (e instanceof MailServiceException mse) return mse;
        boolean auth = e instanceof AuthenticationFailedException;
        String reason = !auth ? reason(e, server)
                : method.isOAuth2() ? tr("mail.error.oauth-login", method.provider().label())
                : tr("mail.error.login");
        LOG.warn("{} {} failed: {}", protocol, server.describe(), e.toString());
        return new MailServiceException(protocol + " " + server.host() + ": " + reason, e, auth);
    }

    private static String reason(Throwable e, ServerSettings server) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof UnknownHostException) return tr("mail.error.unknown-host");
            if (t instanceof ConnectException) return tr("mail.error.refused", String.valueOf(server.port()));
            if (t instanceof SocketTimeoutException) return tr("mail.error.timeout");
            if (t instanceof CertificateException) return tr("mail.error.certificate");
            if (t instanceof SSLException) return tr("mail.error.tls", t.getMessage());
        }
        String message = e.getMessage();
        return message == null || message.isBlank() ? e.getClass().getSimpleName() : message;
    }
}
