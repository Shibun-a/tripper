package com.embabel.tripper.safety;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlanHtmlSanitizerTest {

    private final PlanHtmlSanitizer sanitizer = new PlanHtmlSanitizer();

    @Test
    void stripsScriptTagsButKeepsContent() {
        String out = sanitizer.sanitize("<h4>Day 1</h4><script>alert(1)</script><p>Visit the museum.</p>");
        assertFalse(out.contains("script"));
        assertFalse(out.contains("alert"));
        assertTrue(out.contains("Day 1"));
        assertTrue(out.contains("Visit the museum."));
    }

    @Test
    void stripsEventHandlerAttributes() {
        String out = sanitizer.sanitize("<img src=\"https://example.com/x.jpg\" onerror=\"alert(1)\" alt=\"x\">");
        assertFalse(out.contains("onerror"));
        assertTrue(out.contains("https://example.com/x.jpg"));
    }

    @Test
    void dropsJavascriptHrefs() {
        String out = sanitizer.sanitize("<a href=\"javascript:alert(1)\">click</a>");
        assertFalse(out.contains("javascript:"));
        assertTrue(out.contains("click"));
    }

    @Test
    void stripsIframesAndForms() {
        String out = sanitizer.sanitize(
                "<p>ok</p><iframe src=\"https://evil.example\"></iframe><form action=\"https://evil.example\"><input></form>");
        assertFalse(out.contains("iframe"));
        assertFalse(out.contains("form"));
        assertFalse(out.contains("input"));
        assertTrue(out.contains("ok"));
    }

    @Test
    void keepsPlanStructureAndImageAttributes() {
        String out = sanitizer.sanitize(
                "<h4>Route</h4><ul><li><strong>Day 1</strong>: Paris</li></ul>"
                        + "<img src=\"https://example.com/x.jpg\" class=\"styled-image-thick\" width=\"800\" alt=\"Louvre\">"
                        + "<a href=\"https://example.com/page\">more</a>");
        assertTrue(out.contains("<h4>"));
        assertTrue(out.contains("<li>"));
        assertTrue(out.contains("class=\"styled-image-thick\""));
        assertTrue(out.contains("width=\"800\""));
        assertTrue(out.contains("href=\"https://example.com/page\""));
    }

    @Test
    void blankInputBecomesEmpty() {
        assertTrue(sanitizer.sanitize(null).isEmpty());
        assertTrue(sanitizer.sanitize("  ").isEmpty());
    }
}
