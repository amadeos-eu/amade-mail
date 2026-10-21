package eu.amadeos.apps.mail.service;

import eu.amadeos.apps.mail.service.HtmlSanitizer.SanitizedHtml;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HtmlSanitizerTest {
    private final HtmlSanitizer sanitizer = new HtmlSanitizer();

    private static String body(SanitizedHtml html) {
        return html.document().body().html();
    }

    @Test
    void removesActiveContent() {
        SanitizedHtml html = sanitizer.sanitize("""
                <p onclick="steal()">Hello</p>
                <script>alert(1)</script>
                <iframe src="https://evil.example"></iframe>
                <form action="https://evil.example"><input name="pw"></form>
                <a href="javascript:alert(1)">bad</a>
                <a href="https://example.org/">good</a>
                """, false);
        String out = body(html);
        assertTrue(out.contains("Hello"), out);
        assertFalse(out.contains("onclick"), out);
        assertFalse(out.contains("script"), out);
        assertFalse(out.contains("iframe"), out);
        assertFalse(out.contains("<form"), out);
        assertFalse(out.contains("<input"), out);
        assertFalse(out.contains("javascript:"), out);
        assertTrue(out.contains("href=\"https://example.org/\""), out);
    }

    // --8<-- [start:blocks-remote]
    @Test
    void blocksRemoteImagesAndKeepsEmbeddedOnes() {
        SanitizedHtml html = sanitizer.sanitize("""
                <img src="https://tracker.example/pixel.gif" width="1" height="1">
                <img src="http://cdn.example/logo.png" alt="Logo">
                <img src="cid:logo@example">
                <div style="background-image: url('https://cdn.example/bg.png'); color: red">x</div>
                """, false);
        String out = body(html);
        assertEquals(3, html.blockedRemote());
        assertTrue(html.hasBlockedRemoteContent());
        assertFalse(out.contains("tracker.example"), out);
        assertFalse(out.contains("cdn.example"), out);
        assertTrue(out.contains("src=\"cid:logo@example\""), out);
        assertTrue(out.contains("color: red"), out);
    }
    // --8<-- [end:blocks-remote]

    @Test
    void keepsRemoteImagesWhenAllowed() {
        SanitizedHtml html = sanitizer.sanitize("<img src=\"https://cdn.example/logo.png\">", true);
        assertEquals(0, html.blockedRemote());
        assertTrue(body(html).contains("https://cdn.example/logo.png"));
    }

    @Test
    void addsBaseStyle() {
        SanitizedHtml html = sanitizer.sanitize("<p>x</p>", false);
        assertTrue(html.document().head().html().contains("font-family"));
        assertEquals("http://www.w3.org/1999/xhtml",
                org.jsoup.helper.W3CDom.convert(html.document()).getDocumentElement().getNamespaceURI());
    }

    @Test
    void convertsHtmlToPlainText() {
        String text = sanitizer.toPlainText("<p>Hello <b>Ralf</b>,</p><p>line one<br>line two</p><script>x()</script>"
                + "<p>A &amp; B</p>");
        assertEquals("Hello Ralf,\n\nline one\nline two\n\nA & B", text);
    }
}
