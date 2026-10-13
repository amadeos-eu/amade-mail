package org.datazuul.amadeos.mail.bootstrap;

import com.google.inject.Guice;
import com.google.inject.Injector;
import org.datazuul.amadeos.mail.repository.ConfigDirectory;
import org.datazuul.amadeos.mail.ui.main.MainPresenter;
import org.datazuul.amadeos.mail.ui.main.MainWindow;
import org.datazuul.amadeos.mail.ui.swing.AmadeUI;

import javax.swing.*;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Entry point. Arguments may contain a {@code mailto:} URI, as passed by the desktop entry.
 */
public final class AmadeMailApplication {
    private AmadeMailApplication() {
    }

    public static void main(String[] args) {
        // Jakarta Mail: decode RFC 2231/2047 encoded attachment names.
        System.setProperty("mail.mime.decodefilename", "true");
        System.setProperty("mail.mime.decodeparameters", "true");
        SwingUtilities.invokeLater(() -> {
            AmadeUI.install();
            ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
            Injector injector = Guice.createInjector(new ApplicationModule(executor, ConfigDirectory.fromEnvironment()));
            MainWindow window = injector.getInstance(MainWindow.class);
            MainPresenter presenter = injector.getInstance(MainPresenter.class);
            window.bind(presenter);
            window.setVisible(true);
            presenter.start(List.of(args));
        });
    }
}
