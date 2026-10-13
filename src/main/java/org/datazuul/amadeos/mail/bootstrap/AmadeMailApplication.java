package org.datazuul.amadeos.mail.bootstrap;

import org.datazuul.amadeos.commons.app.AmadeApplication;
import org.datazuul.amadeos.commons.app.AppInfo;
import org.datazuul.amadeos.mail.ui.main.MainPresenter;
import org.datazuul.amadeos.mail.ui.main.MainWindow;

import java.util.List;

/**
 * Entry point. Arguments may contain a {@code mailto:} URI, as passed by the desktop entry.
 */
public final class AmadeMailApplication {
    public static final AppInfo APP = new AppInfo("amade-mail", "Mailer", "mail");

    private AmadeMailApplication() {
    }

    public static void main(String[] args) {
        // Jakarta Mail: decode RFC 2231/2047 encoded attachment names.
        System.setProperty("mail.mime.decodefilename", "true");
        System.setProperty("mail.mime.decodeparameters", "true");
        AmadeApplication.launch(APP, injector -> {
            MainWindow window = injector.getInstance(MainWindow.class);
            MainPresenter presenter = injector.getInstance(MainPresenter.class);
            window.bind(presenter);
            window.setVisible(true);
            presenter.start(List.of(args));
        }, new MailModule());
    }
}
