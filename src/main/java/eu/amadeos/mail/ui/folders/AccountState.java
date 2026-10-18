package eu.amadeos.mail.ui.folders;

import eu.amadeos.mail.domain.FolderRole;
import eu.amadeos.mail.domain.MailFolder;

import java.util.List;
import java.util.Optional;

/**
 * What the folder tree knows about one account.
 *
 * @param folders folders from the last successful listing
 * @param status  connection state
 * @param error   last error message, empty if none
 */
public record AccountState(List<MailFolder> folders, Status status, String error) {

    public enum Status {LOADING, ONLINE, OFFLINE}

    public static final AccountState LOADING = new AccountState(List.of(), Status.LOADING, "");

    public AccountState {
        folders = List.copyOf(folders);
        error = error == null ? "" : error;
    }

    public static AccountState online(List<MailFolder> folders) {
        return new AccountState(folders, Status.ONLINE, "");
    }

    /** Keeps the folders already known so that the tree does not collapse on a network hiccup. */
    public AccountState offline(String message) {
        return new AccountState(folders, Status.OFFLINE, message);
    }

    public AccountState loading() {
        return new AccountState(folders, Status.LOADING, "");
    }

    public AccountState withFolder(MailFolder updated) {
        return new AccountState(folders.stream()
                .map(f -> f.fullName().equals(updated.fullName()) ? updated : f)
                .toList(), status, error);
    }

    public Optional<MailFolder> folder(FolderRole role) {
        return folders.stream().filter(f -> f.role() == role && f.selectable()).findFirst();
    }

    public Optional<MailFolder> folder(String fullName) {
        return folders.stream().filter(f -> f.fullName().equals(fullName)).findFirst();
    }

    /** Unread messages that deserve attention: everything except junk and trash. */
    public int unread() {
        return folders.stream()
                .filter(f -> f.role() != FolderRole.JUNK && f.role() != FolderRole.TRASH)
                .mapToInt(f -> Math.max(0, f.unread()))
                .sum();
    }
}
