package eu.amadeos.apps.mail.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal JSON reader for OAuth token responses: objects become {@link Map}s, arrays {@link List}s,
 * numbers {@link Double}s.
 */
final class Json {
    private final String text;
    private int pos;

    private Json(String text) {
        this.text = text;
    }

    static Map<String, Object> parseObject(String text) {
        Json json = new Json(text == null ? "" : text);
        json.skipSpace();
        if (!(json.value() instanceof Map<?, ?> map)) throw new IllegalArgumentException("Not a JSON object");
        json.skipSpace();
        if (json.pos != json.text.length()) throw json.error("Trailing characters");
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) map;
        return result;
    }

    private Object value() {
        skipSpace();
        if (pos >= text.length()) throw error("Unexpected end");
        char c = text.charAt(pos);
        return switch (c) {
            case '{' -> object();
            case '[' -> array();
            case '"' -> string();
            case 't' -> literal("true", Boolean.TRUE);
            case 'f' -> literal("false", Boolean.FALSE);
            case 'n' -> literal("null", null);
            default -> number();
        };
    }

    private Map<String, Object> object() {
        Map<String, Object> map = new LinkedHashMap<>();
        pos++;
        skipSpace();
        if (consume('}')) return map;
        do {
            skipSpace();
            String key = string();
            skipSpace();
            expect(':');
            map.put(key, value());
            skipSpace();
        } while (consume(','));
        expect('}');
        return map;
    }

    private List<Object> array() {
        List<Object> list = new ArrayList<>();
        pos++;
        skipSpace();
        if (consume(']')) return list;
        do {
            list.add(value());
            skipSpace();
        } while (consume(','));
        expect(']');
        return list;
    }

    private String string() {
        expect('"');
        StringBuilder sb = new StringBuilder();
        while (pos < text.length()) {
            char c = text.charAt(pos++);
            if (c == '"') return sb.toString();
            if (c != '\\') {
                sb.append(c);
                continue;
            }
            if (pos >= text.length()) break;
            char e = text.charAt(pos++);
            switch (e) {
                case 'b' -> sb.append('\b');
                case 'f' -> sb.append('\f');
                case 'n' -> sb.append('\n');
                case 'r' -> sb.append('\r');
                case 't' -> sb.append('\t');
                case 'u' -> {
                    if (pos + 4 > text.length()) throw error("Bad unicode escape");
                    sb.append((char) Integer.parseInt(text.substring(pos, pos + 4), 16));
                    pos += 4;
                }
                default -> sb.append(e);
            }
        }
        throw error("Unterminated string");
    }

    private Double number() {
        int start = pos;
        while (pos < text.length() && "+-0123456789.eE".indexOf(text.charAt(pos)) >= 0) pos++;
        if (start == pos) throw error("Unexpected character");
        try {
            return Double.valueOf(text.substring(start, pos));
        } catch (NumberFormatException e) {
            throw error("Bad number");
        }
    }

    private Object literal(String word, Object value) {
        if (!text.startsWith(word, pos)) throw error("Unexpected character");
        pos += word.length();
        return value;
    }

    private void skipSpace() {
        while (pos < text.length() && Character.isWhitespace(text.charAt(pos))) pos++;
    }

    private boolean consume(char c) {
        if (pos < text.length() && text.charAt(pos) == c) {
            pos++;
            return true;
        }
        return false;
    }

    private void expect(char c) {
        if (!consume(c)) throw error("Expected '" + c + "'");
    }

    private IllegalArgumentException error(String message) {
        return new IllegalArgumentException(message + " at position " + pos);
    }
}
