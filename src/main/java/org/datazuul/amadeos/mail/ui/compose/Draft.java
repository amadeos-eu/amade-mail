package org.datazuul.amadeos.mail.ui.compose;

/**
 * Initial content of a compose window.
 *
 * @param accountId sending account, null for the first account
 * @param to        recipients
 * @param cc        copy recipients
 * @param subject   subject
 * @param body      text, including quoted original and signature
 * @param inReplyTo Message-ID of the answered message, empty for new messages
 */
public record Draft(String accountId, String to, String cc, String subject, String body, String inReplyTo) {
    public static Draft empty(String accountId, String body) {
        return new Draft(accountId, "", "", "", body, "");
    }
}
