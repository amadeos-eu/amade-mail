package org.datazuul.amadeos.mail.service;

import org.datazuul.amadeos.mail.domain.Account;

/**
 * Supplies account passwords to the mail gateway. Implementations may ask the user, so
 * {@link #password(Account)} is only called from background threads.
 */
public interface CredentialProvider {
    /**
     * @throws MailServiceException if no password is known and the user cancelled the prompt
     */
    String password(Account account);

    /** Forgets a password the server rejected, so that the next call asks again. */
    void rejected(Account account);

    /** The user entered a new password in Mail Options. */
    void passwordChanged(String accountId, String password);
}
