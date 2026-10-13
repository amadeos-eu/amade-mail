package org.datazuul.amadeos.mail.repository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps passwords in memory for the running session only. Used when no keyring is available.
 */
public final class SessionPasswordStore implements PasswordStore {
    private final Map<String, String> passwords = new ConcurrentHashMap<>();

    @Override
    public Optional<String> lookup(String accountId) {
        return Optional.ofNullable(passwords.get(accountId));
    }

    @Override
    public void store(String accountId, String label, String password) {
        passwords.put(accountId, password);
    }

    @Override
    public void delete(String accountId) {
        passwords.remove(accountId);
    }

    @Override
    public boolean isPersistent() {
        return false;
    }
}
