package com.newsapp.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class HtmlTextTest {

    @Test
    void nullStaysNull() {
        assertNull(HtmlText.toPlain(null));
    }

    @Test
    void paragraphsBecomeLinesAndTagsAreDropped() {
        assertEquals("First.\nSecond.", HtmlText.toPlain("<p>First.</p><p>Second.</p>"));
        assertEquals("a link and bold", HtmlText.toPlain("<a href=\"https://x.test\">a link</a> and <b>bold</b>"));
    }

    @Test
    void decodesEntities() {
        assertEquals("Fish & chips \"today\" it's", HtmlText.toPlain("Fish &amp; chips &quot;today&quot; it&#39;s"));
        assertEquals("a b", HtmlText.toPlain("a&nbsp;b"));
    }

    @Test
    void collapsesRunsOfWhitespace() {
        assertEquals("one\n\ntwo", HtmlText.toPlain("<p>one</p>\n\n\n\n<p>  two  </p>"));
    }
}
