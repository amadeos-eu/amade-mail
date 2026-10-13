package org.datazuul.amadeos.mail.service;

/**
 * A mail operation failed. The message is meant for the status line and error dialogs.
 */
public class MailServiceException extends RuntimeException {
    private final boolean authenticationFailure;

    public MailServiceException(String message, Throwable cause) {
        this(message, cause, false);
    }

    public MailServiceException(String message, Throwable cause, boolean authenticationFailure) {
        super(message, cause);
        this.authenticationFailure = authenticationFailure;
    }

    public MailServiceException(String message) {
        this(message, null, false);
    }

    /** True if the server rejected user name or password. */
    public boolean isAuthenticationFailure() {
        return authenticationFailure;
    }
}
