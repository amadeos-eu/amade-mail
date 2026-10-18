package eu.amadeos.apps.mail.bootstrap;

import com.google.inject.AbstractModule;
import com.google.inject.Scopes;
import eu.amadeos.apps.mail.repository.AccountRepository;
import eu.amadeos.apps.mail.repository.FileAccountRepository;
import eu.amadeos.apps.mail.service.CalendarHandoff;
import eu.amadeos.apps.mail.service.CredentialProvider;
import eu.amadeos.apps.mail.service.ProcessCalendarHandoff;
import eu.amadeos.apps.mail.service.JakartaMailGateway;
import eu.amadeos.apps.mail.service.MailGateway;
import eu.amadeos.apps.mail.ui.common.PromptingCredentialProvider;
import eu.amadeos.apps.mail.ui.main.MainPresenter;
import eu.amadeos.apps.mail.ui.main.MainView;
import eu.amadeos.apps.mail.ui.main.MainWindow;

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
