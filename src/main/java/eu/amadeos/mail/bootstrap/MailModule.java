package eu.amadeos.mail.bootstrap;

import com.google.inject.AbstractModule;
import com.google.inject.Scopes;
import eu.amadeos.mail.repository.AccountRepository;
import eu.amadeos.mail.repository.FileAccountRepository;
import eu.amadeos.mail.service.CalendarHandoff;
import eu.amadeos.mail.service.CredentialProvider;
import eu.amadeos.mail.service.ProcessCalendarHandoff;
import eu.amadeos.mail.service.JakartaMailGateway;
import eu.amadeos.mail.service.MailGateway;
import eu.amadeos.mail.ui.common.PromptingCredentialProvider;
import eu.amadeos.mail.ui.main.MainPresenter;
import eu.amadeos.mail.ui.main.MainView;
import eu.amadeos.mail.ui.main.MainWindow;

/**
 * Mailer bindings. Executor, directories, theme, keyring and desktop launcher come from
 * {@link eu.amadeos.commons.app.CommonsModule}.
 */
public final class MailModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(AccountRepository.class).to(FileAccountRepository.class).in(Scopes.SINGLETON);
        bind(CredentialProvider.class).to(PromptingCredentialProvider.class).in(Scopes.SINGLETON);
        bind(MailGateway.class).to(JakartaMailGateway.class).in(Scopes.SINGLETON);
        bind(CalendarHandoff.class).to(ProcessCalendarHandoff.class).in(Scopes.SINGLETON);
        bind(MainWindow.class).in(Scopes.SINGLETON);
        bind(MainView.class).to(MainWindow.class);
        bind(MainPresenter.class).in(Scopes.SINGLETON);
    }
}
