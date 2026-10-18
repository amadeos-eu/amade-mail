package eu.amadeos.apps.mail.ui.folders;

import eu.amadeos.apps.mail.domain.Account;
import eu.amadeos.apps.mail.domain.AccountColor;
import eu.amadeos.apps.mail.domain.FolderRole;
import eu.amadeos.apps.mail.domain.MailFolder;
import org.junit.jupiter.api.Test;

import javax.swing.tree.DefaultMutableTreeNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FolderTreeBuilderTest {
    private final FolderTreeBuilder builder = new FolderTreeBuilder();

    private static Account account(String id, String name) {
        Account blank = Account.blank(AccountColor.BLUE);
        return new Account(id, name, "", id + "@example.org", id, blank.imap(), blank.smtp(), "", AccountColor.TEAL);
    }

    private static MailFolder folder(String account, String fullName, int unread) {
        String name = fullName.substring(fullName.lastIndexOf('/') + 1);
        return new MailFolder(account, fullName, name, '/', FolderRole.detect(fullName, name), true, unread, 10);
    }

    private static List<String> childLabels(DefaultMutableTreeNode node) {
        List<String> labels = new ArrayList<>();
        for (int i = 0; i < node.getChildCount(); i++) {
            labels.add(FolderTreePanel.label((TreeItem) ((DefaultMutableTreeNode) node.getChildAt(i)).getUserObject()));
        }
        return labels;
    }

    @Test
    void singleAccountHasNoUnifiedInbox() {
        Account a = account("a", "Privat");
        DefaultMutableTreeNode root = builder.build(List.of(a), Map.of("a", AccountState.online(List.of(folder("a", "INBOX", 0)))));
        assertEquals(1, root.getChildCount());
        assertInstanceOf(TreeItem.AccountItem.class, ((DefaultMutableTreeNode) root.getChildAt(0)).getUserObject());
    }

    @Test
    void severalAccountsGetAllInboxesWithSummedUnread() {
        Account a = account("a", "Privat");
        Account b = account("b", "Work");
        DefaultMutableTreeNode root = builder.build(List.of(a, b), Map.of(
                "a", AccountState.online(List.of(folder("a", "INBOX", 2))),
                "b", AccountState.online(List.of(folder("b", "INBOX", 3), folder("b", "Junk", 7)))));
        assertEquals(List.of("All inboxes (5)", "Privat (2)", "Work (3)"), childLabels(root));
    }

    @Test
    void sortsSpecialFoldersFirstAndBuildsHierarchy() {
        Account a = account("a", "Privat");
        List<MailFolder> folders = List.of(
                folder("a", "Zeta", 0),
                folder("a", "Trash", 0),
                folder("a", "Projekte/amadeOS", 1),
                folder("a", "Sent", 0),
                folder("a", "INBOX", 4),
                folder("a", "Archive", 0),
                folder("a", "Drafts", 0));
        DefaultMutableTreeNode root = builder.build(List.of(a), Map.of("a", AccountState.online(folders)));
        DefaultMutableTreeNode accountNode = (DefaultMutableTreeNode) root.getChildAt(0);
        assertEquals(List.of("Inbox (4)", "Drafts", "Sent", "Trash", "Archive", "Projekte", "Zeta"), childLabels(accountNode));

        DefaultMutableTreeNode projekte = (DefaultMutableTreeNode) accountNode.getChildAt(5);
        TreeItem.FolderItem placeholder = (TreeItem.FolderItem) projekte.getUserObject();
        assertFalse(placeholder.folder().selectable(), "parent not listed by the server");
        assertEquals(List.of("amadeOS (1)"), childLabels(projekte));
    }

    @Test
    void offlineAccountKeepsFoldersAndShowsState() {
        Account a = account("a", "Vereine");
        AccountState state = AccountState.online(List.of(folder("a", "INBOX", 1))).offline("Server not found.");
        DefaultMutableTreeNode root = builder.build(List.of(a), Map.of("a", state));
        assertEquals(List.of("Vereine (1) (offline)"), childLabels(root));
        assertEquals(1, root.getChildAt(0).getChildCount());
    }
}
