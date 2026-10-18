package eu.amadeos.mail.ui.main;

import eu.amadeos.mail.domain.Account;
import eu.amadeos.mail.domain.MailPreferences;
import eu.amadeos.mail.domain.MessageSummary;
import eu.amadeos.mail.ui.compose.Draft;
import eu.amadeos.mail.ui.folders.AccountState;
import eu.amadeos.mail.ui.view.DisplayedMessage;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * What the presenter needs from the main window. All methods run on the EDT.
 */
public interface MainView {
    void showFolders(List<Account> accounts, Map<String, AccountState> states);

    void selectFirstInbox();

    void showMessages(List<MessageSummary> messages, boolean unified);

    void updateMessage(MessageSummary message);

    void removeMessages(Collection<MessageSummary> messages);

    MessageSummary selectedMessage();

    List<MessageSummary> selectedMessages();

    void selectRelative(int delta);

    void showMessage(DisplayedMessage message);

    void clearMessage(String text);

    void showText(String text);

    void setStatus(String text);

    void setContext(String title);

    void setShowHtml(boolean html);

    void showError(String message);

    boolean confirm(String message);

    void openOptions(List<Account> accounts, MailPreferences preferences, String page, boolean addAccount);

    void openCompose(List<Account> accounts, Draft draft);

    /** Asks where to save; null if cancelled. */
    Path chooseSaveFile(String suggestedName);

    /** True if a compose window has unsent text. */
    boolean hasOpenComposeWindows();

    void dispose();
}
