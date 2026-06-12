package io.github.shibuna.tripsmith.eval;

import java.util.List;

/**
 * Java-facing seam for the LLM-as-judge. Defined in Java (implemented by the Kotlin
 * {@code TravelPlanJudge}) so the Java evaluation harness depends only on Java types — the Maven
 * build compiles Java in the same phase as Kotlin, so a Java→Kotlin reference would not resolve.
 */
public interface PlanJudge {

    JudgeScores judge(
            String planText,
            List<String> interests,
            List<String> constraints,
            List<String> expectedThemes,
            List<String> expectedCountries
    );
}
