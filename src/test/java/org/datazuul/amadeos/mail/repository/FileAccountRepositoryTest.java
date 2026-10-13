package org.datazuul.amadeos.mail.repository;

import org.datazuul.amadeos.mail.domain.Account;
import org.datazuul.amadeos.mail.domain.AccountColor;
import org.datazuul.amadeos.mail.domain.AuthMethod;
import org.datazuul.amadeos.mail.domain.ConnectionSecurity;
import org.datazuul.amadeos.mail.domain.ServerSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FileAccountRepositoryTest {
    @TempDir
    Path dir;

    private static Account account(String id, String name, AccountColor color) {
        return new Account(id, name, "Ralf Eichinger", "ralf@example.org", "ralf",
                new ServerSettings("imap.example.org", 993, ConnectionSecurity.TLS),
                new ServerSettings("smtp.example.org", 587, ConnectionSecurity.STARTTLS),
                "-- Ralf\nexample.org", color);
    }

    @Test
    void missingFileMeansNoAccounts() {
        assertEquals(List.of(), new FileAccountRepository(dir.resolve("accounts.conf")).load());
    }

    @Test
    void savesAndLoadsAccountsInOrder() {
        FileAccountRepository repository = new FileAccountRepository(dir.resolve("mail/accounts.conf"));
        List<Account> accounts = List.of(account("b2", "Work", AccountColor.TEAL), account("a1", "Privat", AccountColor.CORAL));
        repository.save(accounts);
        assertEquals(accounts, repository.load());
    }

    @Test
    void neverWritesPasswords() throws IOException {
        Path file = dir.resolve("accounts.conf");
        new FileAccountRepository(file).save(List.of(account("a1", "Privat", AccountColor.BLUE)));
        for (String line : Files.readAllLines(file)) {
            // ".auth=PASSWORD" names the login method, it is not a secret.
            if (line.startsWith("#") || line.endsWith(".auth=PASSWORD")) continue;
            assertFalse(line.toLowerCase().contains("password"), line);
        }
    }

    @Test
    void toleratesBrokenValues() throws IOException {
        Path file = dir.resolve("accounts.conf");
        Files.writeString(file, """
                accounts=x1, bad id ,
                account.x1.email=x@example.org
                account.x1.imap.host=imap.example.org
                account.x1.imap.port=not-a-number
                account.x1.imap.security=STARTTLS
                account.x1.smtp.security=UNKNOWN
                account.x1.color=NOPE
                """);
        List<Account> accounts = new FileAccountRepository(file).load();
        assertEquals(1, accounts.size());
        Account a = accounts.getFirst();
        assertEquals(143, a.imap().port());
        assertEquals(ConnectionSecurity.STARTTLS, a.imap().security());
        assertEquals(ConnectionSecurity.STARTTLS, a.smtp().security());
        assertEquals(587, a.smtp().port());
        assertEquals(AccountColor.BLUE, a.color());
        assertEquals(AuthMethod.PASSWORD, a.auth());
    }

    @Test
    void keepsTheAuthenticationMethod() {
        FileAccountRepository repository = new FileAccountRepository(dir.resolve("accounts.conf"));
        Account plain = account("a1", "Privat", AccountColor.BLUE);
        Account gmail = new Account("g1", "Gmail", "Ralf", "ralf@gmail.com", "ralf@gmail.com", plain.imap(),
                plain.smtp(), "", AccountColor.TEAL, AuthMethod.OAUTH2_GOOGLE);
        repository.save(List.of(plain, gmail));
        assertEquals(List.of(plain, gmail), repository.load());
    }
}
