package com.embabel.tripper.safety;

import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Service;

/**
 * Whitelist sanitizer for LLM-generated plan HTML. The plan body is rendered with th:utext,
 * so anything the model (or a prompt-injected web page it researched) emits would otherwise
 * reach the browser unescaped. Jsoup drops every element and attribute not explicitly allowed
 * here — script, iframe, event handlers, non-http(s) URL schemes — while keeping the
 * headings/paragraphs/lists/links/images the plan prompt asks for.
 */
@Service
public class PlanHtmlSanitizer {

    private final Safelist safelist = Safelist.relaxed()
            // postProcessHtml's styleImages adds a class to every img; allow it to survive.
            .addAttributes("img", "class")
            .addTags("figure", "figcaption")
            // relaxed() permits ftp hrefs; plans should only ever link http(s) (mailto stays harmless).
            .removeProtocols("a", "href", "ftp");

    public String sanitize(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        return Jsoup.clean(html, safelist);
    }
}
