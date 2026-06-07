package com.embabel.tripper.safety;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Pattern;

@Service
public class SensitiveDataRedactor {

    private static final List<ReplacementRule> RULES = List.of(
            new ReplacementRule(Pattern.compile("(?i)(api[_-]?key|token|secret|password|client[_-]?secret)\\s*[:=]\\s*['\\\"]?[^\\s'\\\"<>,;]+"), "$1=[REDACTED]"),
            new ReplacementRule(Pattern.compile("(?i)bearer\\s+[a-z0-9._\\-]+"), "Bearer [REDACTED]"),
            new ReplacementRule(Pattern.compile("\\bsk-[A-Za-z0-9_\\-]{16,}\\b"), "[REDACTED_OPENAI_KEY]"),
            new ReplacementRule(Pattern.compile("\\bgh[pousr]_[A-Za-z0-9_]{20,}\\b"), "[REDACTED_GITHUB_TOKEN]"),
            new ReplacementRule(Pattern.compile("\\b[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}\\b"), "[REDACTED_EMAIL]")
    );

    public String redact(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        String redacted = value;
        for (ReplacementRule rule : RULES) {
            redacted = rule.pattern().matcher(redacted).replaceAll(rule.replacement());
        }
        return redacted;
    }

    public boolean containsSensitiveData(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        return RULES.stream().anyMatch(rule -> rule.pattern().matcher(value).find());
    }

    private record ReplacementRule(Pattern pattern, String replacement) {
    }
}
