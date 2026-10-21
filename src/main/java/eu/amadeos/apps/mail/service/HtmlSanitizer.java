package eu.amadeos.apps.mail.service;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.jsoup.safety.Cleaner;
import org.jsoup.safety.Safelist;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Makes HTML mail safe to display: removes scripts, frames, forms, event handlers, external style
 * sheets and, unless allowed, every reference to remote content (tracking pixels, remote images,
 * CSS backgrounds). Images embedded in the message ({@code cid:}) are kept.
 */
public final class HtmlSanitizer {
    static final String XHTML_NAMESPACE = "http://www.w3.org/1999/xhtml";
    private static final Pattern CSS_URL = Pattern.compile("url\\s*\\(([^)]*)\\)", Pattern.CASE_INSENSITIVE);

    /** Default look of the message view; matches the plain text view. */
    static final String BASE_CSS = "body { font-family: sans-serif; font-size: 13px; background-color: #ffffff; "
            + "color: #111111; margin: 8px; } img { border: 0; } span.blocked-image { border: 1px dashed #8c827a; "
            + "color: #6b625b; padding: 0 4px; } blockquote { margin-left: 8px; "
            + "padding-left: 8px; border-left: 2px solid #8c827a; }";

    private final Safelist safelist = Safelist.relaxed()
            .addTags("font", "center", "hr", "big", "s")
            .addAttributes(":all", "style", "align", "valign", "width", "height", "bgcolor", "dir", "title")
            .addAttributes("table", "border", "cellpadding", "cellspacing", "bgcolor")
            .addAttributes("td", "nowrap", "bgcolor")
            .addAttributes("th", "nowrap", "bgcolor")
            .addAttributes("font", "color", "face", "size")
            .addProtocols("img", "src", "cid", "data", "http", "https");

    // --8<-- [start:sanitize]
    /**
     * @param html        the text/html body of a message
     * @param allowRemote true to keep references to remote images
     */
    public SanitizedHtml sanitize(String html, boolean allowRemote) {
        Document dirty = Jsoup.parse(html == null ? "" : html);
        Document clean = new Cleaner(safelist).clean(dirty);
        int blocked = 0;

        for (Element img : clean.select("img")) {
            String src = img.attr("src").strip();
            String lower = src.toLowerCase(Locale.ROOT);
            if (lower.startsWith("data:") && !lower.startsWith("data:image/")) {
                img.removeAttr("src");
            } else if (isRemote(lower) && !allowRemote) {
                img.removeAttr("src");
                blocked++;
            }
            if (img.attr("src").isBlank()) replaceWithAltText(img);
        }
        for (Element element : clean.select("[style]")) {
            String style = element.attr("style");
            Matcher matcher = CSS_URL.matcher(style);
            StringBuilder result = new StringBuilder();
            boolean changed = false;
            while (matcher.find()) {
                String target = matcher.group(1).replace("\"", "").replace("'", "").strip().toLowerCase(Locale.ROOT);
                boolean keep = target.startsWith("cid:") || (allowRemote && isRemote(target));
                if (!keep) {
                    if (isRemote(target)) blocked++;
                    matcher.appendReplacement(result, "none");
                    changed = true;
                }
            }
            if (changed) {
                matcher.appendTail(result);
                element.attr("style", result.toString());
            }
        }
        for (Element link : clean.select("a[href]")) {
            link.removeAttr("target");
        }

        clean.head().appendElement("style").attr("type", "text/css").appendText(BASE_CSS);
        // W3CDom puts the elements into this namespace; Flying Saucer's default XHTML style sheet only
        // matches elements in the XHTML namespace.
        Element root = clean.selectFirst("html");
        if (root != null) root.attr("xmlns", XHTML_NAMESPACE);
        clean.outputSettings().syntax(Document.OutputSettings.Syntax.xml).prettyPrint(false);
        return new SanitizedHtml(clean, blocked);
    }
    // --8<-- [end:sanitize]

    /**
     * Flying Saucer paints an image without a source as a black box of the given size, so an image that
     * lost its source becomes its alternative text in a dashed frame, or disappears when it has none
     * (tracking pixels never have one).
     */
    // --8<-- [start:alt-text]
    private static void replaceWithAltText(Element img) {
        String alt = img.attr("alt").strip();
        if (alt.isEmpty()) {
            img.remove();
        } else {
            img.replaceWith(new Element("span").addClass("blocked-image").text(alt));
        }
    }
    // --8<-- [end:alt-text]

    /** Plain text rendering of an HTML body, used to quote HTML-only messages in replies. */
    public String toPlainText(String html) {
        Document document = Jsoup.parse(html == null ? "" : html);
        document.outputSettings().prettyPrint(false);
        document.select("br").after("\\n");
        document.select("p, h1, h2, h3, h4, h5, h6, blockquote, pre, table").before("\\n\\n");
        document.select("div, tr, li").before("\\n");
        String marked = Jsoup.clean(document.body().html(), "", Safelist.none(),
                new Document.OutputSettings().prettyPrint(false));
        String text = Parser.unescapeEntities(marked, false).replace("\\n", "\n");
        return text.replaceAll("[ \\t\\x0B\\f\\r]+\n", "\n").replaceAll("\n{3,}", "\n\n").strip();
    }

    private static boolean isRemote(String lowerCaseUrl) {
        return lowerCaseUrl.startsWith("http:") || lowerCaseUrl.startsWith("https:") || lowerCaseUrl.startsWith("//");
    }

    /**
     * @param document        cleaned document, ready for the renderer
     * @param blockedRemote   number of remote references that were removed
     */
    public record SanitizedHtml(Document document, int blockedRemote) {
        public boolean hasBlockedRemoteContent() {
            return blockedRemote > 0;
        }
    }
}
