package org.datazuul.amadeos.mail.ui.view;

import org.datazuul.amadeos.mail.domain.MessageContent.Attachment;

/**
 * User actions inside the message view.
 */
public interface MessageViewActions {
    void loadRemoteImagesOnce();

    void alwaysLoadRemoteImagesFromSender();

    void linkClicked(String uri);

    void openAttachment(Attachment attachment);

    void saveAttachment(Attachment attachment);

    /** The message carries an appointment: hand it to the Calendar. */
    void scheduleInCalendar();
}
