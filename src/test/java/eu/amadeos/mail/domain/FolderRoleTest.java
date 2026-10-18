package eu.amadeos.mail.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FolderRoleTest {
    @Test
    void inboxIsAlwaysInbox() {
        assertEquals(FolderRole.INBOX, FolderRole.detect("INBOX", "INBOX"));
        assertEquals(FolderRole.INBOX, FolderRole.detect("inbox", "inbox"));
    }

    @Test
    void specialUseAttributesWin() {
        assertEquals(FolderRole.SENT, FolderRole.detect("Gesendete Elemente", "Gesendete Elemente", "\\HasNoChildren", "\\Sent"));
        assertEquals(FolderRole.TRASH, FolderRole.detect("[Gmail]/Bin", "Bin", "\\Trash"));
    }

    @Test
    void fallsBackToCommonNames() {
        assertEquals(FolderRole.DRAFTS, FolderRole.detect("INBOX.Drafts", "Drafts"));
        assertEquals(FolderRole.JUNK, FolderRole.detect("Spam", "Spam"));
        assertEquals(FolderRole.TRASH, FolderRole.detect("Papierkorb", "Papierkorb"));
        assertEquals(FolderRole.NORMAL, FolderRole.detect("Projekte/amadeOS", "amadeOS"));
    }
}
