package eu.amadeos.apps.mail.ui.folders;

import eu.amadeos.apps.mail.domain.Account;
import eu.amadeos.apps.mail.domain.MailFolder;

/**
 * User objects of the folder tree nodes.
 */
public sealed interface TreeItem {

    /** Stable identity used to keep selection and expansion across rebuilds. */
    String key();

    /** The virtual folder that merges the inboxes of all accounts. */
    record UnifiedInbox(int unread) implements TreeItem {
        @Override
        public String key() {
            return "*unified";
        }
    }

    record AccountItem(Account account, AccountState state) implements TreeItem {
        @Override
        public String key() {
            return account.id();
        }
    }

    /**
     * A folder; {@code folder.selectable()} is false for container folders and for parents the
     * server did not list.
     */
    record FolderItem(Account account, MailFolder folder) implements TreeItem {
        @Override
        public String key() {
            return account.id() + "/" + folder.fullName();
        }
    }
}
