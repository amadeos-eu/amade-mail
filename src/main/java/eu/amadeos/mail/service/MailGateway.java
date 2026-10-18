package eu.amadeos.mail.service;

import eu.amadeos.mail.domain.Account;
import eu.amadeos.mail.domain.MailFolder;
import eu.amadeos.mail.domain.MessageContent;
import eu.amadeos.mail.domain.MessageSummary;
import eu.amadeos.mail.domain.OutgoingMessage;

import java.util.List;

/**
 * Blocking access to IMAP and SMTP servers. All methods may take long and must not run on the EDT;
 * they throw {@link MailServiceException} on failure.
 */
public interface MailGateway extends AutoCloseable {

    /** Logs in to IMAP and SMTP with the given password without keeping the connections. */
    void testConnection(Account account, String password);

    /** All folders of the account with unread and total counts. */
    List<MailFolder> listFolders(Account account);

    /** Unread and total count of one folder, without opening it. */
    MailFolder refreshCounts(Account account, MailFolder folder);

    /** The newest {@code limit} messages of the folder, newest first. */
    List<MessageSummary> listMessages(Account account, String folder, int limit);

    /** Loads and parses a message; the server marks it as seen. */
    MessageContent loadMessage(Account account, String folder, long uid);

    /** The raw RFC 822 source, at most {@code maxBytes} long. */
    String loadSource(Account account, String folder, long uid, int maxBytes);

    void setSeen(Account account, String folder, List<Long> uids, boolean seen);

    void move(Account account, String fromFolder, List<Long> uids, String toFolder);

    /** Flags the messages as deleted and expunges them. */
    void deletePermanently(Account account, String folder, List<Long> uids);

    /** Sends the message and stores a copy in {@code sentFolder} if that is not null. */
    void send(OutgoingMessage message, String sentFolder);

    /** Closes the connection of one account, e.g. after its settings changed. */
    void disconnect(String accountId);

    @Override
    void close();
}
