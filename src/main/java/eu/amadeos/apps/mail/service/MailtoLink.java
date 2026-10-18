package eu.amadeos.apps.mail.service;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * A parsed {@code mailto:} URI (RFC 6068), used for the command line ({@code amade-mail mailto:...}) and
 * for links in messages.
 */
public record MailtoLink(String to, String cc, String bcc, String subject, String body) {

    public static boolean isMailto(String uri) {
        return uri != null && uri.strip().toLowerCase(Locale.ROOT).startsWith("mailto:");
    }

    public static MailtoLink parse(String uri) {
        String rest = uri.strip().substring("mailto:".length());
        int question = rest.indexOf('?');
        String to = decode(question < 0 ? rest : rest.substring(0, question));
        String cc = "", bcc = "", subject = "", body = "";
        if (question >= 0) {
            for (String pair : rest.substring(question + 1).split("&")) {
                int eq = pair.indexOf('=');
                if (eq <= 0) continue;
                String key = pair.substring(0, eq).toLowerCase(Locale.ROOT);
                String value = decode(pair.substring(eq + 1));
                switch (key) {
                    case "to" -> to = to.isEmpty() ? value : to + ", " + value;
                    case "cc" -> cc = value;
                    case "bcc" -> bcc = value;
                    case "subject" -> subject = value;
                    case "body" -> body = value;
                    default -> {
                        // other headers (e.g. In-Reply-To) are ignored for safety
                    }
                }
            }
        }
        return new MailtoLink(to, cc, bcc, subject, body);
    }

    /** Percent-decoding without turning "+" into a space, which URLDecoder would do. */
    private static String decode(String value) {
        try {
            return URLDecoder.decode(value.replace("+", "%2B"), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return value;
        }
    }
}
