package eu.amadeos.mail.ui.view;

import eu.amadeos.mail.service.HtmlSanitizer.SanitizedHtml;
import org.jsoup.helper.W3CDom;
import org.xhtmlrenderer.simple.FSScrollPane;
import org.xhtmlrenderer.simple.XHTMLPanel;
import org.xhtmlrenderer.simple.extend.XhtmlNamespaceHandler;
import org.xhtmlrenderer.swing.BasicPanel;
import org.xhtmlrenderer.swing.FSMouseListener;
import org.xhtmlrenderer.swing.LinkListener;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Renders sanitised HTML mail with Flying Saucer (XHTML + CSS 2.1, no scripting). Link clicks are not
 * followed inside the view but handed to the caller, which asks before opening a browser.
 */
final class HtmlMessagePane extends JPanel {
    /** Base URI of every document; never resolved because {@link MailUserAgent} keeps URIs as they are. */
    private static final String BASE_URI = "file:/amade-mail/message.html";

    private final MailUserAgent userAgent = new MailUserAgent();
    private final XHTMLPanel panel = new XHTMLPanel(userAgent);

    HtmlMessagePane(Consumer<String> linkHandler) {
        super(new BorderLayout());
        for (FSMouseListener listener : new ArrayList<>(panel.getMouseTrackingListeners())) {
            if (listener instanceof LinkListener) panel.removeMouseTrackingListener(listener);
        }
        panel.addMouseTrackingListener(new LinkListener() {
            @Override
            public void linkClicked(BasicPanel source, String uri) {
                linkHandler.accept(uri);
            }
        });
        panel.setBackground(Color.WHITE);
        add(new FSScrollPane(panel), BorderLayout.CENTER);
    }

    void show(SanitizedHtml html, Map<String, byte[]> resources) {
        userAgent.setResources(resources);
        // Explicit XHTML handler: default CSS for HTML elements and <img> as replaced elements.
        panel.setDocument(W3CDom.convert(html.document()), BASE_URI, new XhtmlNamespaceHandler());
    }

    void clear() {
        userAgent.setResources(Map.of());
        panel.setDocument(W3CDom.convert(org.jsoup.Jsoup.parse("<html><body></body></html>")), BASE_URI,
                new XhtmlNamespaceHandler());
    }
}
