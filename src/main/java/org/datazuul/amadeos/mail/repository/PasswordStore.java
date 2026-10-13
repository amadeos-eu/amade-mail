package org.datazuul.amadeos.mail.repository;

import java.util.Optional;

/**
 * Keeps account passwords outside the configuration file.
 */
public interface PasswordStore {
    Optional<String> lookup(String accountId);

    void store(String accountId, String label, String password);

    void delete(String accountId);

    /** False if passwords only live for the running session. */
    boolean isPersistent();
}
