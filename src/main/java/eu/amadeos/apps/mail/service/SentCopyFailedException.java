package eu.amadeos.apps.mail.service;

/**
 * The message was sent, but storing the copy in the Sent folder failed.
 */
public class SentCopyFailedException extends MailServiceException {
    public SentCopyFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
