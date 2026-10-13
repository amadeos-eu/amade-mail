package org.datazuul.amadeos.mail.repository;

import org.datazuul.amadeos.mail.domain.Account;

import java.util.List;

/**
 * Persists the configured accounts in their display order.
 */
public interface AccountRepository {
    List<Account> load();

    void save(List<Account> accounts);
}
