package org.datazuul.amadeos.mail.domain;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * A message written in the compose window.
 *
 * @param account     sending account
 * @param to          comma separated recipients
 * @param cc          comma separated copy recipients, may be empty
 * @param bcc         comma separated blind copy recipients, may be empty
 * @param subject     subject
 * @param body        plain text body
 * @param attachments files to attach
 * @param inReplyTo   Message-ID of the answered message, may be empty
 */
public record OutgoingMessage(Account account, String to, String cc, String bcc, String subject, String body,
                              List<Path> attachments, String inReplyTo) {

    public OutgoingMessage {
        Objects.requireNonNull(account, "account");
        to = to == null ? "" : to.strip();
        cc = cc == null ? "" : cc.strip();
        bcc = bcc == null ? "" : bcc.strip();
        subject = subject == null ? "" : subject;
        body = body == null ? "" : body;
        attachments = List.copyOf(attachments);
        inReplyTo = inReplyTo == null ? "" : inReplyTo.strip();
    }
}
