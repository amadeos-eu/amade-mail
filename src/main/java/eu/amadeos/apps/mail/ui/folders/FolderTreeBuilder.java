package eu.amadeos.apps.mail.ui.folders;

import static eu.amadeos.commons.i18n.Texts.tr;

import eu.amadeos.apps.mail.domain.Account;
import eu.amadeos.apps.mail.domain.FolderRole;
import eu.amadeos.apps.mail.domain.MailFolder;

import javax.swing.tree.DefaultMutableTreeNode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Builds the folder tree: an optional "All inboxes" node, then one node per account with its folder
 * hierarchy. Special folders come first in the usual order, the rest alphabetically.
 */
public final class FolderTreeBuilder {

    public static final Comparator<MailFolder> ORDER = Comparator
            .comparingInt((MailFolder f) -> f.role().order())
            .thenComparing(f -> f.name().toLowerCase(Locale.ROOT));

    public DefaultMutableTreeNode build(List<Account> accounts, Map<String, AccountState> states) {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode(tr("folders.title"));
        if (accounts.size() > 1) {
            int unread = 0;
            for (Account account : accounts) {
                AccountState state = states.getOrDefault(account.id(), AccountState.LOADING);
                unread += state.folder(FolderRole.INBOX).map(f -> Math.max(0, f.unread())).orElse(0);
            }
            root.add(new DefaultMutableTreeNode(new TreeItem.UnifiedInbox(unread), false));
        }
        for (Account account : accounts) {
            AccountState state = states.getOrDefault(account.id(), AccountState.LOADING);
            DefaultMutableTreeNode accountNode = new DefaultMutableTreeNode(new TreeItem.AccountItem(account, state));
            addFolders(accountNode, account, state.folders());
            root.add(accountNode);
        }
        return root;
    }

    private static void addFolders(DefaultMutableTreeNode accountNode, Account account, List<MailFolder> folders) {
        Map<String, DefaultMutableTreeNode> nodes = new HashMap<>();
        List<MailFolder> sorted = new ArrayList<>(folders);
        // Parents before children, so that a parent listed by the server is reused, not synthesised.
        sorted.sort(Comparator.comparingInt((MailFolder f) -> depth(f)).thenComparing(ORDER));
        for (MailFolder folder : sorted) {
            if (nodes.containsKey(folder.fullName())) continue;
            DefaultMutableTreeNode parent = parentNode(accountNode, account, folder, nodes);
            DefaultMutableTreeNode node = new DefaultMutableTreeNode(new TreeItem.FolderItem(account, folder));
            nodes.put(folder.fullName(), node);
            parent.add(node);
        }
        sortChildren(accountNode);
    }

    private static DefaultMutableTreeNode parentNode(DefaultMutableTreeNode accountNode, Account account,
                                                     MailFolder folder, Map<String, DefaultMutableTreeNode> nodes) {
        String separator = String.valueOf(folder.separator());
        int last = folder.separator() == 0 ? -1 : folder.fullName().lastIndexOf(separator);
        if (last <= 0) return accountNode;
        String parentName = folder.fullName().substring(0, last);
        DefaultMutableTreeNode parent = nodes.get(parentName);
        if (parent != null) return parent;
        // The server did not list the parent; add a non-selectable placeholder.
        String shortName = parentName.substring(parentName.lastIndexOf(separator) + 1);
        MailFolder placeholder = new MailFolder(folder.accountId(), parentName, shortName, folder.separator(),
                FolderRole.detect(parentName, shortName), false, -1, -1);
        DefaultMutableTreeNode grandParent = parentNode(accountNode, account, placeholder, nodes);
        parent = new DefaultMutableTreeNode(new TreeItem.FolderItem(account, placeholder));
        nodes.put(parentName, parent);
        grandParent.add(parent);
        return parent;
    }

    private static int depth(MailFolder folder) {
        if (folder.separator() == 0) return 0;
        int depth = 0;
        for (char c : folder.fullName().toCharArray()) if (c == folder.separator()) depth++;
        return depth;
    }

    private static void sortChildren(DefaultMutableTreeNode node) {
        List<DefaultMutableTreeNode> children = new ArrayList<>();
        for (int i = 0; i < node.getChildCount(); i++) children.add((DefaultMutableTreeNode) node.getChildAt(i));
        children.sort(Comparator.comparing(n -> ((TreeItem.FolderItem) n.getUserObject()).folder(), ORDER));
        node.removeAllChildren();
        for (DefaultMutableTreeNode child : children) {
            node.add(child);
            sortChildren(child);
        }
    }
}
