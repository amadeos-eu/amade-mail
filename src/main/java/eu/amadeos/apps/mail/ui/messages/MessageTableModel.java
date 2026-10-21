package eu.amadeos.apps.mail.ui.messages;

import static eu.amadeos.commons.i18n.Texts.tr;

import eu.amadeos.commons.i18n.Texts;

import eu.amadeos.apps.mail.domain.MessageSummary;

import javax.swing.table.AbstractTableModel;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * Rows of the message header list, as in dtmail: status, sender, subject, date and size.
 */
public final class MessageTableModel extends AbstractTableModel {
    public static final int STATUS = 0;
    public static final int SENDER = 1;
    public static final int SUBJECT = 2;
    public static final int DATE = 3;
    public static final int SIZE = 4;

    private static final String[] COLUMNS = {"", tr("column.sender"), tr("column.subject"), tr("column.date"),
            tr("column.size")};

    private final List<MessageSummary> rows = new ArrayList<>();
    private boolean unified;

    public void setMessages(List<MessageSummary> messages, boolean unified) {
        rows.clear();
        rows.addAll(messages);
        this.unified = unified;
        fireTableDataChanged();
    }

    /** True if the rows come from several accounts (All inboxes). */
    public boolean isUnified() {
        return unified;
    }

    public MessageSummary get(int modelRow) {
        return rows.get(modelRow);
    }

    public List<MessageSummary> messages() {
        return List.copyOf(rows);
    }

    public int indexOf(MessageSummary message) {
        for (int i = 0; i < rows.size(); i++) if (rows.get(i).sameMessage(message)) return i;
        return -1;
    }

    public void update(MessageSummary message) {
        int index = indexOf(message);
        if (index < 0) return;
        rows.set(index, message);
        fireTableRowsUpdated(index, index);
    }

    public void remove(Collection<MessageSummary> messages) {
        rows.removeIf(row -> messages.stream().anyMatch(row::sameMessage));
        fireTableDataChanged();
    }

    public int unreadCount() {
        return (int) rows.stream().filter(m -> !m.seen()).count();
    }

    @Override
    public int getRowCount() {
        return rows.size();
    }

    @Override
    public int getColumnCount() {
        return COLUMNS.length;
    }

    @Override
    public String getColumnName(int column) {
        return COLUMNS[column];
    }

    @Override
    public Class<?> getColumnClass(int column) {
        return switch (column) {
            case DATE -> Instant.class;
            case SIZE -> Long.class;
            default -> String.class;
        };
    }

    @Override
    public Object getValueAt(int row, int column) {
        MessageSummary m = rows.get(row);
        return switch (column) {
            case STATUS -> status(m);
            case SENDER -> m.sender();
            case SUBJECT -> m.subject();
            case DATE -> m.date();
            case SIZE -> m.size();
            default -> throw new IllegalArgumentException("column " + column);
        };
    }

    /** dtmail-style status letters: N new, A answered, ! flagged, @ attachment. */
    static String status(MessageSummary m) {
        StringBuilder s = new StringBuilder();
        if (!m.seen()) s.append('N');
        if (m.answered()) s.append('A');
        if (m.flagged()) s.append('!');
        if (m.hasAttachments()) s.append('@');
        return s.toString();
    }

    public static String formatDate(Instant instant, ZoneId zone, LocalDate today) {
        if (instant == null) return "";
        var date = instant.atZone(zone);
        String pattern = date.toLocalDate().equals(today) ? "date.today"
                : date.getYear() == today.getYear() ? "date.this-year" : "date.older";
        return DateTimeFormatter.ofPattern(tr(pattern), Texts.messages().locale()).format(date);
    }

    public static String formatSize(long bytes) {
        if (bytes < 0) return "";
        if (bytes < 1024) return bytes + "";
        if (bytes < 1024 * 1024) return Math.round(bytes / 1024.0) + "k";
        return String.format(Locale.ROOT, "%.1fM", bytes / (1024.0 * 1024.0));
    }
}
