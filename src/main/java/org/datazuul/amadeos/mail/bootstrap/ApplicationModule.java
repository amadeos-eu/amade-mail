package org.datazuul.amadeos.mail.bootstrap;

import com.google.inject.AbstractModule;
import com.google.inject.Scopes;
import org.datazuul.amadeos.mail.repository.AccountRepository;
import org.datazuul.amadeos.mail.repository.ConfigDirectory;
import org.datazuul.amadeos.mail.repository.FileAccountRepository;
import org.datazuul.amadeos.mail.repository.PasswordStore;
import org.datazuul.amadeos.mail.repository.SecretToolPasswordStore;
import org.datazuul.amadeos.mail.service.CredentialProvider;
import org.datazuul.amadeos.mail.service.JakartaMailGateway;
import org.datazuul.amadeos.mail.service.MailGateway;
import org.datazuul.amadeos.mail.ui.common.PromptingCredentialProvider;
import org.datazuul.amadeos.mail.ui.main.MainPresenter;
import org.datazuul.amadeos.mail.ui.main.MainView;
import org.datazuul.amadeos.mail.ui.main.MainWindow;

import java.util.concurrent.ExecutorService;

public final class ApplicationModule extends AbstractModule {
    private final ExecutorService executor;
    private final ConfigDirectory configDirectory;

    public ApplicationModule(ExecutorService executor, ConfigDirectory configDirectory) {
        this.executor = executor;
        this.configDirectory = configDirectory;
    }

    @Override
    protected void configure() {
        bind(ExecutorService.class).toInstance(executor);
        bind(ConfigDirectory.class).toInstance(configDirectory);
        bind(AccountRepository.class).to(FileAccountRepository.class).in(Scopes.SINGLETON);
        bind(PasswordStore.class).to(SecretToolPasswordStore.class).in(Scopes.SINGLETON);
        bind(CredentialProvider.class).to(PromptingCredentialProvider.class).in(Scopes.SINGLETON);
        bind(MailGateway.class).to(JakartaMailGateway.class).in(Scopes.SINGLETON);
        bind(MainWindow.class).in(Scopes.SINGLETON);
        bind(MainView.class).to(MainWindow.class);
        bind(MainPresenter.class).in(Scopes.SINGLETON);
    }
}
