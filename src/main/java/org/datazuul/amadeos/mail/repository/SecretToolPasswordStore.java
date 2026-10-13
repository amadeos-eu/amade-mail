package org.datazuul.amadeos.mail.repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Stores passwords in the freedesktop Secret Service (gnome-keyring, KeePassXC, ...) through the
 * {@code secret-tool} command from the Debian package {@code libsecret-tools}. Falls back to a
 * {@link SessionPasswordStore} when the tool or the service is not available.
 */
public final class SecretToolPasswordStore implements PasswordStore {
    private static final Logger LOG = LoggerFactory.getLogger(SecretToolPasswordStore.class);
    private static final String SERVICE = "amade-mail";

    private final String executable;
    private final SessionPasswordStore cache = new SessionPasswordStore();

    public SecretToolPasswordStore() {
        this(findOnPath("secret-tool"));
    }

    SecretToolPasswordStore(String executable) {
        this.executable = executable;
    }

    @Override
    public Optional<String> lookup(String accountId) {
        Optional<String> cached = cache.lookup(accountId);
        if (cached.isPresent() || executable == null) return cached;
        Result result = run(null, executable, "lookup", "service", SERVICE, "account", accountId);
        if (result.exitCode() != 0 || result.output().isEmpty()) return Optional.empty();
        cache.store(accountId, "", result.output());
        return Optional.of(result.output());
    }

    @Override
    public void store(String accountId, String label, String password) {
        cache.store(accountId, label, password);
        if (executable == null) return;
        Result result = run(password, executable, "store", "--label=" + label,
                "service", SERVICE, "account", accountId);
        if (result.exitCode() != 0) {
            LOG.warn("secret-tool could not store the password for {} (exit {}); keeping it for this session only",
                    accountId, result.exitCode());
        }
    }

    @Override
    public void delete(String accountId) {
        cache.delete(accountId);
        if (executable != null) run(null, executable, "clear", "service", SERVICE, "account", accountId);
    }

    @Override
    public boolean isPersistent() {
        return executable != null;
    }

    private static Result run(String input, String... command) {
        try {
            Process process = new ProcessBuilder(List.of(command)).redirectErrorStream(false).start();
            try (OutputStream stdin = process.getOutputStream()) {
                if (input != null) stdin.write(input.getBytes(StandardCharsets.UTF_8));
            }
            // The output is a single password, far below the pipe buffer, so waiting first cannot dead-lock
            // and a hanging keyring (e.g. waiting for an unlock prompt) cannot block the caller forever.
            if (!process.waitFor(30, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return new Result(-1, "");
            }
            String output;
            try (InputStream stdout = process.getInputStream(); InputStream stderr = process.getErrorStream()) {
                output = new String(stdout.readAllBytes(), StandardCharsets.UTF_8);
                stderr.readAllBytes();
            }
            return new Result(process.exitValue(), output);
        } catch (IOException e) {
            LOG.warn("Cannot run secret-tool", e);
            return new Result(-1, "");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Result(-1, "");
        }
    }

    static String findOnPath(String name) {
        String path = System.getenv("PATH");
        if (path == null) return null;
        for (String dir : path.split(File.pathSeparator)) {
            File candidate = new File(dir, name);
            if (candidate.isFile() && candidate.canExecute()) return candidate.getAbsolutePath();
        }
        return null;
    }

    private record Result(int exitCode, String output) {
    }
}
