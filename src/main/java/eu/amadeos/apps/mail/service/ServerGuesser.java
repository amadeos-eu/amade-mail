package eu.amadeos.apps.mail.service;

import static eu.amadeos.commons.i18n.Texts.tr;

import eu.amadeos.apps.mail.domain.AuthMethod;
import eu.amadeos.apps.mail.domain.ConnectionSecurity;
import eu.amadeos.apps.mail.domain.ServerSettings;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Suggests server settings from an e-mail address: a small table of well-known providers, otherwise
 * the common {@code imap.<domain>} / {@code smtp.<domain>} convention.
 *
 * <p>Next step: query the Thunderbird ISPDB (autoconfig) and DNS SRV records (RFC 6186).
 */
public final class ServerGuesser {

    public record Guess(ServerSettings imap, ServerSettings smtp, String username, AuthMethod auth, String hint) {
    }

    private static final String GMAIL_HINT = tr("accounts.hint.oauth", "Google");

    private static final Map<String, Guess> PROVIDERS = Map.ofEntries(
            provider("gmail.com", "imap.gmail.com", 993, ConnectionSecurity.TLS, "smtp.gmail.com", 465,
                    ConnectionSecurity.TLS, AuthMethod.OAUTH2_GOOGLE, GMAIL_HINT),
            provider("googlemail.com", "imap.gmail.com", 993, ConnectionSecurity.TLS, "smtp.gmail.com", 465,
                    ConnectionSecurity.TLS, AuthMethod.OAUTH2_GOOGLE, GMAIL_HINT),
            provider("gmx.de", "imap.gmx.net", 993, ConnectionSecurity.TLS, "mail.gmx.net", 587,
                    ConnectionSecurity.STARTTLS, tr("guess.enable-imap", "GMX")),
            provider("gmx.net", "imap.gmx.net", 993, ConnectionSecurity.TLS, "mail.gmx.net", 587,
                    ConnectionSecurity.STARTTLS, tr("guess.enable-imap", "GMX")),
            provider("web.de", "imap.web.de", 993, ConnectionSecurity.TLS, "smtp.web.de", 587,
                    ConnectionSecurity.STARTTLS, tr("guess.enable-imap", "WEB.DE")),
            provider("t-online.de", "secureimap.t-online.de", 993, ConnectionSecurity.TLS, "securesmtp.t-online.de",
                    465, ConnectionSecurity.TLS, tr("guess.telekom")),
            provider("posteo.de", "posteo.de", 993, ConnectionSecurity.TLS, "posteo.de", 587,
                    ConnectionSecurity.STARTTLS, ""),
            provider("mailbox.org", "imap.mailbox.org", 993, ConnectionSecurity.TLS, "smtp.mailbox.org", 465,
                    ConnectionSecurity.TLS, ""),
            provider("icloud.com", "imap.mail.me.com", 993, ConnectionSecurity.TLS, "smtp.mail.me.com", 587,
                    ConnectionSecurity.STARTTLS, tr("guess.icloud")));

    public Optional<Guess> guess(String email) {
        if (email == null) return Optional.empty();
        String address = email.strip().toLowerCase(Locale.ROOT);
        int at = address.lastIndexOf('@');
        if (at <= 0 || at == address.length() - 1) return Optional.empty();
        String domain = address.substring(at + 1);
        Guess known = PROVIDERS.get(domain);
        if (known != null) {
            return Optional.of(new Guess(known.imap(), known.smtp(), email.strip(), known.auth(), known.hint()));
        }
        return Optional.of(new Guess(
                new ServerSettings("imap." + domain, 993, ConnectionSecurity.TLS),
                new ServerSettings("smtp." + domain, 587, ConnectionSecurity.STARTTLS),
                email.strip(),
                AuthMethod.PASSWORD,
                tr("guess.from-domain")));
    }

    private static Map.Entry<String, Guess> provider(String domain, String imapHost, int imapPort,
                                                     ConnectionSecurity imapSecurity, String smtpHost, int smtpPort,
                                                     ConnectionSecurity smtpSecurity, String hint) {
        return provider(domain, imapHost, imapPort, imapSecurity, smtpHost, smtpPort, smtpSecurity,
                AuthMethod.PASSWORD, hint);
    }

    private static Map.Entry<String, Guess> provider(String domain, String imapHost, int imapPort,
                                                     ConnectionSecurity imapSecurity, String smtpHost, int smtpPort,
                                                     ConnectionSecurity smtpSecurity, AuthMethod auth, String hint) {
        return Map.entry(domain, new Guess(new ServerSettings(imapHost, imapPort, imapSecurity),
                new ServerSettings(smtpHost, smtpPort, smtpSecurity), "", auth, hint));
    }
}
