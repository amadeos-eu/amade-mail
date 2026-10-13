package org.datazuul.amadeos.mail.ui.messages;

import org.datazuul.amadeos.mail.domain.MessageSummary;
import org.datazuul.amadeos.commons.swing.EdtExecutor;
import org.datazuul.amadeos.commons.swing.AmadeUI;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * The message header list with the dtmail button row underneath.
 */
public final class MessageListPanel extends JPanel {
    private final MessageTableModel model = new MessageTableModel();
    private final JTable table = new JTable(model);
    private final JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 6));
    private Function<String, Color> accountColors = id -> null;
    private Consumer<MessageSummary> selectionListener = m -> {
    };
    private boolean updating;

    public MessageListPanel() {
        super(new BorderLayout());

        table.setFillsViewportHeight(true);
        table.setShowVerticalLines(false);
        table.setRowHeight(table.getFontMetrics(table.getFont()).getHeight() + 4);
        table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS);
        TableRowSorter<MessageTableModel> sorter = new TableRowSorter<>(model);
        sorter.setSortsOnUpdates(false);
        table.setRowSorter(sorter);
        Renderer renderer = new Renderer();
        for (int i = 0; i < model.getColumnCount(); i++) table.getColumnModel().getColumn(i).setCellRenderer(renderer);
        width(MessageTableModel.STATUS, 40, 40);
        width(MessageTableModel.SENDER, 170, -1);
        width(MessageTableModel.SUBJECT, 360, -1);
        width(MessageTableModel.DATE, 130, -1);
        width(MessageTableModel.SIZE, 50, 70);
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && !updating) selectionListener.accept(selectedMessage());
        });

        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(AmadeUI.sunken());
        AmadeUI.style(scroll.getViewport(), (viewport, theme) -> viewport.setBackground(theme.fieldBackground()));
        add(scroll, BorderLayout.CENTER);
        add(buttons, BorderLayout.SOUTH);
    }

    private void width(int column, int preferred, int max) {
        TableColumn c = table.getColumnModel().getColumn(column);
        c.setPreferredWidth(preferred);
        if (max > 0) c.setMaxWidth(max);
    }

    public void addButton(String text, Runnable action) {
        JButton button = new JButton(text);
        button.addActionListener(e -> action.run());
        buttons.add(button);
    }

    public void setSelectionListener(Consumer<MessageSummary> listener) {
        this.selectionListener = listener;
    }

    public void setAccountColors(Function<String, Color> accountColors) {
        this.accountColors = accountColors;
    }

    /** Replaces the rows, keeping the selected message if it is still there. */
    public void setMessages(List<MessageSummary> messages, boolean unified) {
        EdtExecutor.requireEdt();
        MessageSummary selected = selectedMessage();
        updating = true;
        try {
            model.setMessages(messages, unified);
            if (selected != null) select(selected);
        } finally {
            updating = false;
        }
        if (selected != null && selectedMessage() == null) selectionListener.accept(null);
    }

    public void update(MessageSummary message) {
        updating = true;
        try {
            model.update(message);
        } finally {
            updating = false;
        }
    }

    /** Removes the rows and selects the message that followed the first removed one. */
    public void remove(Collection<MessageSummary> messages) {
        int[] rows = table.getSelectedRows();
        int next = rows.length == 0 ? -1 : rows[0];
        updating = true;
        try {
            model.remove(messages);
        } finally {
            updating = false;
        }
        if (next >= 0 && table.getRowCount() > 0) {
            int row = Math.min(next, table.getRowCount() - 1);
            table.setRowSelectionInterval(row, row);
            table.scrollRectToVisible(table.getCellRect(row, 0, true));
        } else {
            selectionListener.accept(null);
        }
    }

    public MessageSummary selectedMessage() {
        int row = table.getSelectedRow();
        return row < 0 ? null : model.get(table.convertRowIndexToModel(row));
    }

    public List<MessageSummary> selectedMessages() {
        List<MessageSummary> result = new ArrayList<>();
        for (int row : table.getSelectedRows()) result.add(model.get(table.convertRowIndexToModel(row)));
        return result;
    }

    public List<MessageSummary> messages() {
        return model.messages();
    }

    public void select(MessageSummary message) {
        int modelRow = model.indexOf(message);
        if (modelRow < 0) return;
        int row = table.convertRowIndexToView(modelRow);
        table.setRowSelectionInterval(row, row);
        table.scrollRectToVisible(table.getCellRect(row, 0, true));
    }

    /** Moves the selection by {@code delta} rows (Next / Previous). */
    public void selectRelative(int delta) {
        if (table.getRowCount() == 0) return;
        int row = table.getSelectedRow();
        int target = row < 0 ? 0 : Math.max(0, Math.min(table.getRowCount() - 1, row + delta));
        table.setRowSelectionInterval(target, target);
        table.scrollRectToVisible(table.getCellRect(target, 0, true));
    }

    public int unreadCount() {
        return model.unreadCount();
    }

    public JTable table() {
        return table;
    }

    private final class Renderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable t, Object value, boolean selected, boolean focus,
                                                       int row, int column) {
            MessageSummary m = model.get(t.convertRowIndexToModel(row));
            Object text = switch (column) {
                case MessageTableModel.DATE ->
                        MessageTableModel.formatDate((Instant) value, ZoneId.systemDefault(), LocalDate.now());
                case MessageTableModel.SIZE -> MessageTableModel.formatSize((Long) value);
                default -> value;
            };
            super.getTableCellRendererComponent(t, text, selected, false, row, column);
            setFont(t.getFont().deriveFont(m.seen() ? Font.PLAIN : Font.BOLD));
            setHorizontalAlignment(column == MessageTableModel.SIZE ? RIGHT : LEFT);
            setIcon(null);
            if (column == MessageTableModel.STATUS && model.isUnified()) {
                Color color = accountColors.apply(m.accountId());
                if (color != null) setIcon(AmadeUI.colorDot(color, 8));
            }
            setToolTipText(column == MessageTableModel.SENDER ? m.senderAddress() : null);
            return this;
        }
    }
}
