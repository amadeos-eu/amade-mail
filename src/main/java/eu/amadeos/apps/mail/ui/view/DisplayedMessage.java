package eu.amadeos.apps.mail.ui.view;

import eu.amadeos.apps.mail.domain.MessageContent;
import eu.amadeos.apps.mail.domain.MessageSummary;
import eu.amadeos.apps.mail.service.HtmlSanitizer.SanitizedHtml;

import java.util.Map;

/**
 * Everything the message view needs to show one message.
 *
 * @param summary   the header list row
 * @param content   the parsed message
 * @param text      text for the plain view (the text/plain part, or the HTML part converted to text)
 * @param html      sanitised HTML, or null to show {@code text}
 * @param resources images for the HTML view, keyed by the URI used in the document ("cid:..." or remote URL)
 */
public record DisplayedMessage(MessageSummary summary, MessageContent content, String text, SanitizedHtml html,
                               Map<String, byte[]> resources) {

    public DisplayedMessage {
        text = text == null ? "" : text;
        resources = Map.copyOf(resources);
    }

    public boolean showsHtml() {
        return html != null;
    }

    public String plainText() {
        return text;
    }
}
