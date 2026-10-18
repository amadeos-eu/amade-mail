package eu.amadeos.apps.mail.repository;

import com.google.inject.Inject;
import eu.amadeos.commons.config.AppDirectories;
import eu.amadeos.commons.config.PropertiesFile;
import eu.amadeos.apps.mail.domain.Account;
import eu.amadeos.apps.mail.domain.AccountColor;
import eu.amadeos.apps.mail.domain.AuthMethod;
import eu.amadeos.apps.mail.domain.ConnectionSecurity;
import eu.amadeos.apps.mail.domain.ServerSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Stores accounts in {@code accounts.conf}:
 *
 * <pre>
 * accounts=a1b2c3,d4e5f6
 * account.a1b2c3.name=Privat
 * account.a1b2c3.imap.host=imap.example.org
 * ...
 * </pre>
 *
 * Passwords are never written here.
 */
public final class FileAccountRepository implements AccountRepository {
    private static final Logger LOG = LoggerFactory.getLogger(FileAccountRepository.class);
    static final String FILE_NAME = "accounts.conf";

    private final Path file;

    @Inject
    public FileAccountRepository(AppDirectories directories) {
        this(directories.configFile(FILE_NAME));
    }

    FileAccountRepository(Path file) {
        this.file = file;
    }

    @Override
    public synchronized List<Account> load() {
        Properties p = PropertiesFile.load(file);
        List<Account> accounts = new ArrayList<>();
        String ids = p.getProperty("accounts", "");
        int index = 0;
        for (String id : ids.split(",")) {
            id = id.strip();
            if (id.isEmpty()) continue;
            try {
                accounts.add(read(p, id, index++));
            } catch (RuntimeException e) {
                LOG.warn("Skipping invalid account '{}' in {}", id, file, e);
            }
        }
        return accounts;
    }

    @Override
    public synchronized void save(List<Account> accounts) {
        Properties p = new Properties();
        List<String> ids = new ArrayList<>();
        for (Account account : accounts) {
            ids.add(account.id());
            write(p, account);
        }
        p.setProperty("accounts", String.join(",", ids));
        PropertiesFile.store(file, p, "amade-mail accounts (passwords are kept in the keyring)");
    }

    private static Account read(Properties p, String id, int index) {
        String prefix = "account." + id + ".";
        return new Account(id,
                p.getProperty(prefix + "name", ""),
                p.getProperty(prefix + "displayName", ""),
                p.getProperty(prefix + "email", ""),
                p.getProperty(prefix + "username", ""),
                readServer(p, prefix + "imap.", ConnectionSecurity.TLS, true),
                readServer(p, prefix + "smtp.", ConnectionSecurity.STARTTLS, false),
                p.getProperty(prefix + "signature", ""),
                readColor(p.getProperty(prefix + "color"), index),
                readAuth(p.getProperty(prefix + "auth")));
    }

    private static ServerSettings readServer(Properties p, String prefix, ConnectionSecurity fallback, boolean imap) {
        ConnectionSecurity security = parseSecurity(p.getProperty(prefix + "security"), fallback);
        int defaultPort = imap ? security.defaultImapPort() : security.defaultSmtpPort();
        int port = parsePort(p.getProperty(prefix + "port"), defaultPort);
        return new ServerSettings(p.getProperty(prefix + "host", ""), port, security);
    }

    private static void write(Properties p, Account a) {
        String prefix = "account." + a.id() + ".";
        p.setProperty(prefix + "name", a.name());
        p.setProperty(prefix + "displayName", a.displayName());
        p.setProperty(prefix + "email", a.email());
        p.setProperty(prefix + "username", a.username());
        writeServer(p, prefix + "imap.", a.imap());
        writeServer(p, prefix + "smtp.", a.smtp());
        p.setProperty(prefix + "signature", a.signature());
        p.setProperty(prefix + "color", a.color().name());
        p.setProperty(prefix + "auth", a.auth().name());
    }

    private static void writeServer(Properties p, String prefix, ServerSettings s) {
        p.setProperty(prefix + "host", s.host());
        p.setProperty(prefix + "port", Integer.toString(s.port()));
        p.setProperty(prefix + "security", s.security().name());
    }

    private static ConnectionSecurity parseSecurity(String value, ConnectionSecurity fallback) {
        if (value == null) return fallback;
        try {
            return ConnectionSecurity.valueOf(value.strip());
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    private static int parsePort(String value, int fallback) {
        if (value == null) return fallback;
        try {
            return Integer.parseInt(value.strip());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static AuthMethod readAuth(String value) {
        if (value == null) return AuthMethod.PASSWORD;
        try {
            return AuthMethod.valueOf(value.strip());
        } catch (IllegalArgumentException e) {
            return AuthMethod.PASSWORD;
        }
    }

    private static AccountColor readColor(String value, int index) {
        if (value != null) {
            try {
                return AccountColor.valueOf(value.strip());
            } catch (IllegalArgumentException e) {
                // fall through to the positional colour
            }
        }
        return AccountColor.forIndex(index);
    }
}
