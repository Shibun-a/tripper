package io.github.shibuna.tripsmith.safety;

import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ContentSafetyService {

    private static final Pattern HTML_URL_ATTRIBUTE = Pattern.compile(
            "(?i)\\s(href|src)\\s*=\\s*(['\\\"])(.*?)\\2"
    );

    private static final List<SafetyRule> RULES = List.of(
            new SafetyRule(
                    Pattern.compile("(?i)\\b(ignore|disregard|forget)\\s+(all\\s+)?(previous|prior|above|earlier)\\s+(instructions|rules|messages)\\b"),
                    SafetyFindingCategory.PROMPT_INJECTION,
                    SafetyRiskLevel.HIGH,
                    "Attempts to override higher-priority instructions."
            ),
            new SafetyRule(
                    Pattern.compile("(?i)\\b(system prompt|developer message|hidden instructions|chain of thought)\\b"),
                    SafetyFindingCategory.PROMPT_INJECTION,
                    SafetyRiskLevel.HIGH,
                    "Requests hidden prompt or reasoning material."
            ),
            new SafetyRule(
                    Pattern.compile("(?i)\\b(call|invoke|use|run|execute)\\s+(the\\s+)?(tool|browser|shell|terminal|api|curl|http)\\b"),
                    SafetyFindingCategory.TOOL_MISUSE_REQUEST,
                    SafetyRiskLevel.MEDIUM,
                    "Attempts to instruct tool use from untrusted content."
            ),
            new SafetyRule(
                    Pattern.compile("(?i)\\b(exfiltrate|send|upload|post)\\b.{0,80}\\b(secret|token|api\\s*key|password|credential)\\b"),
                    SafetyFindingCategory.SECRET_EXPOSURE,
                    SafetyRiskLevel.HIGH,
                    "Attempts to expose or transmit sensitive data."
            ),
            new SafetyRule(
                    Pattern.compile("(?i)\\b(new instructions|you are now|act as system|override safety)\\b"),
                    SafetyFindingCategory.PROMPT_INJECTION,
                    SafetyRiskLevel.MEDIUM,
                    "Looks like injected role or instruction text."
            )
    );

    private final SensitiveDataRedactor redactor;

    public ContentSafetyService(SensitiveDataRedactor redactor) {
        this.redactor = redactor;
    }

    public SafetyAssessment assessUntrustedContent(
            String sourceLabel,
            String content
    ) {
        if (content == null || content.isBlank()) {
            return SafetyAssessment.safe(sourceLabel);
        }

        List<SafetyFinding> findings = new ArrayList<>();
        for (SafetyRule rule : RULES) {
            if (rule.pattern().matcher(content).find()) {
                findings.add(new SafetyFinding(rule.category(), rule.riskLevel(), rule.message()));
            }
        }
        if (redactor.containsSensitiveData(content)) {
            findings.add(new SafetyFinding(
                    SafetyFindingCategory.SECRET_EXPOSURE,
                    SafetyRiskLevel.HIGH,
                    "Contains text that resembles a secret, token, password, or email address."
            ));
        }

        SafetyRiskLevel riskLevel = findings.stream()
                .map(SafetyFinding::riskLevel)
                .max(Enum::compareTo)
                .orElse(SafetyRiskLevel.NONE);
        return new SafetyAssessment(sourceLabel, riskLevel, findings);
    }

    public String sanitizeUntrustedTextForPrompt(
            String content,
            SafetyAssessment assessment
    ) {
        if (content == null || content.isBlank()) {
            return "";
        }

        List<String> sanitizedLines = new ArrayList<>();
        for (String line : content.split("\\R")) {
            if (looksLikeInstructionInjection(line)) {
                sanitizedLines.add("[SAFETY_REMOVED_UNTRUSTED_INSTRUCTION]");
            } else {
                sanitizedLines.add(redactor.redact(line));
            }
        }
        String sanitized = String.join("\n", sanitizedLines).replaceAll("\\s+", " ").trim();
        if (sanitized.isBlank() && assessment != null && assessment.isHighRisk()) {
            return "[SAFETY_REMOVED_HIGH_RISK_CONTENT]";
        }
        return sanitized;
    }

    public String sanitizeHtmlLinks(String html) {
        if (html == null || html.isBlank()) {
            return html;
        }
        Matcher matcher = HTML_URL_ATTRIBUTE.matcher(html);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String attribute = matcher.group(1).toLowerCase(Locale.ROOT);
            String quote = matcher.group(2);
            String url = matcher.group(3);
            if (isSafeHttpUrl(url)) {
                matcher.appendReplacement(sb, Matcher.quoteReplacement(" " + attribute + "=" + quote + url + quote));
            } else {
                String replacement = " " + attribute + "=" + quote + "#blocked-by-safety" + quote
                        + " data-safety-blocked-url=" + quote + "true" + quote;
                matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
            }
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    public boolean isSafeHttpUrl(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        try {
            URI uri = URI.create(url.trim());
            String scheme = uri.getScheme();
            return ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                    && uri.getHost() != null
                    && !uri.getHost().isBlank()
                    && uri.getUserInfo() == null;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    public String safeUrlOrNull(String url) {
        return isSafeHttpUrl(url) ? url : null;
    }

    private boolean looksLikeInstructionInjection(String line) {
        if (line == null || line.isBlank()) {
            return false;
        }
        return RULES.stream()
                .filter(rule -> rule.category() == SafetyFindingCategory.PROMPT_INJECTION
                        || rule.category() == SafetyFindingCategory.TOOL_MISUSE_REQUEST)
                .anyMatch(rule -> rule.pattern().matcher(line).find());
    }

    private record SafetyRule(
            Pattern pattern,
            SafetyFindingCategory category,
            SafetyRiskLevel riskLevel,
            String message
    ) {
    }
}
