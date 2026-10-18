package org.datazuul.amadeos.mail.bootstrap;

import com.google.inject.AbstractModule;
import com.google.inject.Scopes;
import org.datazuul.amadeos.mail.repository.AccountRepository;
import org.datazuul.amadeos.mail.repository.FileAccountRepository;
import org.datazuul.amadeos.mail.service.CalendarHandoff;
import org.datazuul.amadeos.mail.service.CredentialProvider;
import org.datazuul.amadeos.mail.service.ProcessCalendarHandoff;
import org.datazuul.amadeos.mail.service.JakartaMailGateway;
import org.datazuul.amadeos.mail.service.MailGateway;
import org.datazuul.amadeos.mail.ui.common.PromptingCredentialProvider;
import org.datazuul.amadeos.mail.ui.main.MainPresenter;
import org.datazuul.amadeos.mail.ui.main.MainView;
import org.datazuul.amadeos.mail.ui.main.MainWindow;

/**
 * Mailer bindings. Executor, directories, theme, keyring and desktop launcher come from
 * {@link org.datazuul.amadeos.commons.app.CommonsModule}.
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
