package io.github.shibuna.tripsmith.agent

import com.embabel.agent.domain.library.InternetResource
import io.github.shibuna.tripsmith.rag.TravelKnowledgeContext
import io.github.shibuna.tripsmith.safety.ContentSafetyService
import org.springframework.stereotype.Component
import java.time.temporal.ChronoUnit

/**
 * Deterministic stand-ins used when a planner LLM call fails after retries: the run completes
 * with an honest, conservative plan assembled from already-validated pipeline artifacts instead
 * of failing outright. Per-POI research failures degrade inline in the agent; these cover the
 * plan structure and HTML body.
 */
@Component
class FallbackPlans(private val contentSafetyService: ContentSafetyService) {

    fun planMeta(
        brief: JourneyTravelBrief,
        poiFindings: PointOfInterestFindings,
    ): ProposedTravelPlanMeta {
        val days = journeyDays(brief, poiFindings)
        return ProposedTravelPlanMeta(
            title = if (isChinese(brief)) "${brief.from}至${brief.to}轻松行程" else "${brief.from} to ${brief.to} itinerary",
            days = days,
            imageLinks = safeResources(poiFindings.pointsOfInterest.flatMap { it.imageLinks }).take(6),
            videoLinks = safeResources(poiFindings.pointsOfInterest.flatMap { it.videoLinks }).take(6),
            pageLinks = safeResources(poiFindings.pointsOfInterest.flatMap { it.links }).take(8),
            countriesVisited = countriesFrom(days),
        )
    }

    fun planHtml(
        brief: JourneyTravelBrief,
        days: List<Day>,
        poiFindings: PointOfInterestFindings,
        knowledgeContext: TravelKnowledgeContext,
    ): String {
        val dayItems = days.joinToString("\n") {
            "<li><strong>${it.date}</strong>: ${html(it.locationAndCountry.replace("+", " "))}</li>"
        }
        val poiItems = poiFindings.pointsOfInterest.joinToString("\n") {
            "<li><strong>${html(it.pointOfInterest.name)}</strong>: ${html(it.pointOfInterest.description)}</li>"
        }
        val knowledgeNote = if (knowledgeContext.hits.isEmpty()) ""
        else "<p>${html("Included ${knowledgeContext.hits.size} user knowledge source(s) where relevant.")}</p>"

        return if (isChinese(brief)) {
            """
            <h4>${html(brief.from)}至${html(brief.to)}${days.size}天轻松行程</h4>
            <p>实时长文生成暂时不可用，以下为基于已完成路线结构、旅行需求和可用研究结果生成的保守行程。</p>
            <h4>每日安排</h4>
            <ul>
            $dayItems
            </ul>
            <h4>重点体验</h4>
            <ul>
            $poiItems
            </ul>
            <p>跨城交通以${transportLabelZh(brief.transportPreference)}为主；出发前请再次核对班次、开放时间、天气和当地交通。</p>
            $knowledgeNote
            """.trimIndent()
        } else {
            """
            <h4>${html(brief.from)} to ${html(brief.to)} ${days.size}-day relaxed itinerary</h4>
            <p>Live long-form generation was temporarily unavailable, so this conservative itinerary was assembled from the route, brief, and available research.</p>
            <h4>Daily route</h4>
            <ul>
            $dayItems
            </ul>
            <h4>Focus experiences</h4>
            <ul>
            $poiItems
            </ul>
            <p>Plan intercity travel by ${html(brief.transportPreference)}. Recheck schedules, opening hours, weather, and local transport before departure.</p>
            $knowledgeNote
            """.trimIndent()
        }
    }

    /**
     * Derive each fallback day's location from the researched points of interest: their
     * locations are Latin-form values produced by earlier, successful pipeline steps, so the
     * fallback stays compatible with the verifier catalog and map links for any route without
     * a transliteration table. Uncovered dates carry the previous location forward.
     */
    fun journeyDays(
        brief: JourneyTravelBrief,
        poiFindings: PointOfInterestFindings,
    ): List<Day> {
        val pois = poiFindings.pointsOfInterest
            .map { it.pointOfInterest }
            .filter { it.location.isNotBlank() }
            .sortedBy { it.fromDate }
        if (pois.isEmpty()) {
            return journeyDaysFromBrief(brief)
        }
        val result = mutableListOf<Day>()
        var lastLocation = pois.first().location
        var cursor = brief.departureDate
        while (!cursor.isAfter(brief.returnDate)) {
            val covering = pois.firstOrNull { !cursor.isBefore(it.fromDate) && !cursor.isAfter(it.toDate) }
            if (covering != null) {
                lastLocation = covering.location
            }
            result.add(Day(cursor, lastLocation))
            cursor = cursor.plusDays(1)
        }
        return result
    }

    /**
     * Last resort with no researched POIs at all: split the trip between the user's own
     * origin and destination wording, normalized to the '+'-separated location form. The
     * verifier may then report route estimates as unavailable (INFO), which is honest.
     */
    private fun journeyDaysFromBrief(brief: JourneyTravelBrief): List<Day> {
        val totalDays = (ChronoUnit.DAYS.between(brief.departureDate, brief.returnDate) + 1).coerceAtLeast(1)
        val daysAtOrigin = if (totalDays >= 6) 3 else (totalDays / 2).coerceAtLeast(1)
        val result = mutableListOf<Day>()
        var cursor = brief.departureDate
        var index = 0L
        while (!cursor.isAfter(brief.returnDate)) {
            val location = if (index < daysAtOrigin) brief.from else brief.to
            result.add(Day(cursor, location.trim().replace(Regex("\\s+"), "+")))
            cursor = cursor.plusDays(1)
            index += 1
        }
        return result
    }

    /** Countries parsed from 'City,+Country' day locations; days without a country part are skipped. */
    private fun countriesFrom(days: List<Day>): List<String> =
        days.mapNotNull { day ->
            day.locationAndCountry
                .substringAfterLast(',', "")
                .replace("+", " ")
                .trim()
                .ifBlank { null }
        }.distinct()

    /** The form's transport values are our own closed set, so a fixed label map is safe here. */
    private fun transportLabelZh(preference: String): String = when (preference.trim().lowercase()) {
        "driving" -> "自驾"
        "train" -> "火车"
        "flying" -> "飞机"
        "cycling" -> "骑行"
        else -> preference
    }

    private fun safeResources(resources: List<InternetResource>): List<InternetResource> =
        resources
            .filter { contentSafetyService.isSafeHttpUrl(it.url) }
            .map { InternetResource(it.url, it.summary) }

    private fun html(value: String): String =
        value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
}
