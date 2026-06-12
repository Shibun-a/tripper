package com.embabel.tripper.agent

import com.embabel.agent.domain.library.InternetResource
import com.embabel.common.util.StringTransformer
import com.embabel.tripper.safety.ContentSafetyService
import com.embabel.tripper.safety.PlanHtmlSanitizer
import com.embabel.tripper.util.ImageChecker
import org.springframework.stereotype.Component

/**
 * Final display-safety pass over a completed plan: strips markdown fences, whitelist-sanitizes
 * the LLM HTML, styles images, filters link lists down to safe http(s) URLs and drops unsafe
 * Airbnb URLs. Runs once, after verification and accommodation lookup.
 */
@Component
class PlanHtmlPostProcessor(
    private val planHtmlSanitizer: PlanHtmlSanitizer,
    private val contentSafetyService: ContentSafetyService,
) {

    fun process(plan: TravelPlan): TravelPlan =
        plan.copy(
            proposal = plan.proposal.copy(
                plan = StringTransformer.transform(
                    plan.proposal.plan, listOf(
                        stripCodeFence,
                        // Whitelist pass before styleImages so the class attribute it adds survives.
                        sanitizeHtml,
                        styleImages,
                        removeUnsafeLinks,
                        ImageChecker.removeInvalidImageLinks,
                    )
                ),
                pageLinks = safeResources(plan.proposal.pageLinks),
                imageLinks = safeResources(plan.proposal.imageLinks),
                videoLinks = safeResources(plan.proposal.videoLinks),
            ),
            stays = plan.stays.map { stay ->
                stay.copy(airbnbUrl = contentSafetyService.safeUrlOrNull(stay.airbnbUrl))
            },
        )

    // Some models (e.g. via generateText) wrap the HTML body in a ```html ... ``` markdown fence;
    // strip it so the literal backticks do not show in the rendered plan.
    private val stripCodeFence = StringTransformer { html ->
        html.trim()
            .replace(Regex("^```[a-zA-Z]*\\s*"), "")
            .replace(Regex("\\s*```$"), "")
            .trim()
    }

    // Whitelist-sanitize LLM HTML before display: only safe structural tags/attributes and
    // http(s) URLs survive. This is the output-side counterpart to the input-side RAG checks.
    private val sanitizeHtml = StringTransformer { html -> planHtmlSanitizer.sanitize(html) }

    private val styleImages = StringTransformer { html ->
        html.replace(
            "<img",
            "<img class=\"styled-image-thick\""
        )
    }

    private val removeUnsafeLinks = StringTransformer { html ->
        contentSafetyService.sanitizeHtmlLinks(html)
    }

    private fun safeResources(resources: List<InternetResource>): List<InternetResource> =
        resources
            .filter { contentSafetyService.isSafeHttpUrl(it.url) }
            .map { InternetResource(it.url, it.summary) }
}
