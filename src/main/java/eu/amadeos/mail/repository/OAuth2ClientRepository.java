package eu.amadeos.mail.repository;

import com.google.inject.Inject;
import eu.amadeos.commons.config.AppDirectories;
import eu.amadeos.commons.config.PropertiesFile;
import eu.amadeos.mail.domain.OAuth2Client;
import eu.amadeos.mail.domain.OAuth2Provider;

import java.nio.file.Path;
import java.util.Optional;
import java.util.Properties;

/**
 * Stores the OAuth client registered for each provider in {@code oauth2.conf}:
 *
 * <pre>
 * google.clientId=1234-abc.apps.googleusercontent.com
 * google.clientSecret=GOCSPX-...
 * </pre>
 */
public class OAuth2ClientRepository {
    static final String FILE_NAME = "oauth2.conf";

    private final Path file;

    @Inject
    public OAuth2ClientRepository(AppDirectories directories) {
        this(directories.configFile(FILE_NAME));
    }

    OAuth2ClientRepository(Path file) {
        this.file = file;
    }

    public synchronized Optional<OAuth2Client> load(OAuth2Provider provider) {
        Properties p = PropertiesFile.load(file);
        OAuth2Client client = new OAuth2Client(p.getProperty(provider.id() + ".clientId"),
                p.getProperty(provider.id() + ".clientSecret"));
        return client.isComplete() ? Optional.of(client) : Optional.empty();
    }

    public synchronized void save(OAuth2Provider provider, OAuth2Client client) {
        Properties p = PropertiesFile.load(file);
        p.setProperty(provider.id() + ".clientId", client.clientId());
        p.setProperty(provider.id() + ".clientSecret", client.clientSecret());
        PropertiesFile.store(file, p, "amade-mail OAuth clients (tokens are kept in the keyring)");
    }
}
