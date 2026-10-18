package eu.amadeos.apps.mail.ui.folders;

import static eu.amadeos.apps.mail.i18n.Texts.tr;

import eu.amadeos.apps.mail.domain.Account;
import eu.amadeos.apps.mail.domain.MailFolder;
import eu.amadeos.commons.swing.EdtExecutor;
import eu.amadeos.commons.swing.AmadeUI;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * The folder column: accounts and their IMAP folders, with unread counts.
 */
public final class FolderTreePanel extends JPanel {
    private final FolderTreeBuilder builder = new FolderTreeBuilder();
    private final JTree tree = new JTree(new DefaultTreeModel(new DefaultMutableTreeNode(tr("folders.title"))));
    private final Set<String> collapsed = new HashSet<>();
    private Consumer<TreeItem> selectionListener = item -> {
    };
    private boolean rebuilding;

    public FolderTreePanel() {
        super(new BorderLayout(0, 3));
        JLabel title = new JLabel(tr("folders.title"));
        title.setFont(title.getFont().deriveFont(Font.PLAIN, 11f));
        add(title, BorderLayout.NORTH);

        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
        tree.setRowHeight(0);
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        tree.setCellRenderer(new Renderer());
        ToolTipManager.sharedInstance().registerComponent(tree);
        tree.addTreeSelectionListener(e -> {
            if (!rebuilding) selectionListener.accept(selectedItem());
        });
        tree.addTreeExpansionListener(new javax.swing.event.TreeExpansionListener() {
            @Override
            public void treeExpanded(javax.swing.event.TreeExpansionEvent event) {
                if (!rebuilding) collapsed.remove(key(event.getPath()));
            }

            @Override
            public void treeCollapsed(javax.swing.event.TreeExpansionEvent event) {
                if (!rebuilding) collapsed.add(key(event.getPath()));
            }
        });
        JScrollPane scroll = new JScrollPane(tree);
        scroll.setBorder(AmadeUI.sunken());
        add(scroll, BorderLayout.CENTER);
        setPreferredSize(new Dimension(200, 400));
        setMinimumSize(new Dimension(120, 100));
    }

    public void setSelectionListener(Consumer<TreeItem> listener) {
        this.selectionListener = listener;
    }

    /** Rebuilds the tree, keeping selection and expanded folders. */
    public void update(List<Account> accounts, Map<String, AccountState> states) {
        EdtExecutor.requireEdt();
        String selectedKey = selectedItem() == null ? null : selectedItem().key();
        rebuilding = true;
        try {
            DefaultMutableTreeNode root = builder.build(accounts, states);
            tree.setModel(new DefaultTreeModel(root));
            TreePath selectedPath = null;
            List<DefaultMutableTreeNode> nodes = new ArrayList<>();
            Enumeration<javax.swing.tree.TreeNode> all = root.preorderEnumeration();
            while (all.hasMoreElements()) nodes.add((DefaultMutableTreeNode) all.nextElement());
            for (DefaultMutableTreeNode node : nodes) {
                if (!(node.getUserObject() instanceof TreeItem item)) continue;
                TreePath path = new TreePath(node.getPath());
                if (!node.isLeaf() && !collapsed.contains(item.key())) tree.expandPath(path);
                if (item.key().equals(selectedKey)) selectedPath = path;
            }
            if (selectedPath != null) tree.setSelectionPath(selectedPath);
        } finally {
            rebuilding = false;
        }
    }

    public TreeItem selectedItem() {
        TreePath path = tree.getSelectionPath();
        if (path == null) return null;
        Object user = ((DefaultMutableTreeNode) path.getLastPathComponent()).getUserObject();
        return user instanceof TreeItem item ? item : null;
    }

    /** Selects the first inbox (the unified one if there are several accounts). */
    public void selectFirstInbox() {
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) tree.getModel().getRoot();
        Enumeration<javax.swing.tree.TreeNode> all = root.preorderEnumeration();
        while (all.hasMoreElements()) {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) all.nextElement();
            Object user = node.getUserObject();
            boolean inbox = user instanceof TreeItem.UnifiedInbox
                    || (user instanceof TreeItem.FolderItem f && f.folder().role() == eu.amadeos.apps.mail.domain.FolderRole.INBOX);
            if (inbox) {
                tree.setSelectionPath(new TreePath(node.getPath()));
                return;
            }
        }
    }

    public JTree tree() {
        return tree;
    }

    private static String key(TreePath path) {
        Object user = ((DefaultMutableTreeNode) path.getLastPathComponent()).getUserObject();
        return user instanceof TreeItem item ? item.key() : "";
    }

    /** Text of a tree row, e.g. "INBOX (2)"; package visible for tests. */
    static String label(TreeItem item) {
        return switch (item) {
            case TreeItem.UnifiedInbox u -> withCount(tr("folders.all-inboxes"), u.unread());
            case TreeItem.AccountItem a -> {
                String text = withCount(a.account().label(), a.state().unread());
                yield switch (a.state().status()) {
                    case LOADING -> text + " …";
                    case OFFLINE -> text + " " + tr("folders.offline");
                    case ONLINE -> text;
                };
            }
            case TreeItem.FolderItem f -> withCount(displayName(f.folder()), f.folder().unread());
        };
    }

    private static String displayName(MailFolder folder) {
        return "INBOX".equalsIgnoreCase(folder.fullName()) ? tr("folders.inbox") : folder.name();
    }

    private static String withCount(String text, int unread) {
        return unread > 0 ? text + " (" + unread + ")" : text;
    }

    private static final class Renderer extends DefaultTreeCellRenderer {
        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected, boolean expanded,
                                                      boolean leaf, int row, boolean hasFocus) {
            super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);
            Object user = ((DefaultMutableTreeNode) value).getUserObject();
            if (!(user instanceof TreeItem item)) return this;
            setText(label(item));
            Font base = tree.getFont();
            boolean bold = switch (item) {
                case TreeItem.UnifiedInbox u -> true;
                case TreeItem.AccountItem a -> true;
                case TreeItem.FolderItem f -> f.folder().unread() > 0;
            };
            setFont(base.deriveFont(bold ? Font.BOLD : Font.PLAIN));
            switch (item) {
                case TreeItem.AccountItem a -> {
                    setIcon(AmadeUI.colorDot(a.account().color().color(), 10));
                    setToolTipText(a.state().error().isEmpty() ? a.account().email() : a.state().error());
                }
                case TreeItem.FolderItem f -> {
                    setIcon(null);
                    setToolTipText(f.folder().total() >= 0 ? tr("folders.messages", f.folder().total()) : null);
                    if (!f.folder().selectable() && !selected) setForeground(AmadeUI.theme().textInactive());
                }
                case TreeItem.UnifiedInbox u -> {
                    setIcon(null);
                    setToolTipText(tr("folders.all-inboxes.tooltip"));
                }
            }
            return this;
        }
    }
}
