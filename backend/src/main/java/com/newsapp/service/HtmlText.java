package com.newsapp.service;

import java.util.regex.Pattern;
import org.springframework.web.util.HtmlUtils;

/** Turns the HTML the Guardian returns for trailText/body into plain text. */
final class HtmlText {

    private static final Pattern BLOCK_END = Pattern.compile("(?i)</(p|h[1-6]|li|blockquote|div|figure)>|<br\\s*/?>");
    private static final Pattern TAG = Pattern.compile("<[^>]*>");
    private static final Pattern SPACES_AROUND_NEWLINE = Pattern.compile("[ \\t]*\\n[ \\t]*");
    private static final Pattern BLANK_LINES = Pattern.compile("\\n{3,}");
    private static final Pattern SPACES = Pattern.compile("[ \\t]{2,}");

    private HtmlText() {}

    /** Paragraph-like elements become line breaks, other tags are dropped, entities are decoded. Null-safe. */
    static String toPlain(String html) {
        if (html == null) {
            return null;
        }
        String text = BLOCK_END.matcher(html).replaceAll("\n");
        text = TAG.matcher(text).replaceAll("");
        text = HtmlUtils.htmlUnescape(text).replace(' ', ' ');
        text = SPACES_AROUND_NEWLINE.matcher(text).replaceAll("\n");
        text = SPACES.matcher(text).replaceAll(" ");
        text = BLANK_LINES.matcher(text).replaceAll("\n\n");
        return text.strip();
    }
}
