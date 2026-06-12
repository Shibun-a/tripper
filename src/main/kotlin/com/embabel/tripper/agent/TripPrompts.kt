package com.embabel.tripper.agent

import com.embabel.tripper.rag.TravelKnowledgeContext
import com.embabel.tripper.verification.PlanVerificationResult

/**
 * Prompt templates for the travel-planning flow, kept as pure functions so wording changes are
 * reviewable in one place and never tangled with orchestration. The tool-safety policy text is
 * passed in by the caller because the agent owns tool selection.
 */
internal object TripPrompts {

    /**
     * Instruction so the LLM writes natural-language content in the user's chosen UI language,
     * while keeping machine-consumed fields (locationAndCountry, place names, URLs) in Latin form
     * so the verifier coordinate catalog, Airbnb URLs and Google Maps links keep working.
     */
    fun languageInstruction(brief: JourneyTravelBrief): String =
        """
        Write all natural-language content (titles, headings, descriptions and prose) in ${brief.language}.
        IMPORTANT: keep place names and the "locationAndCountry" field in Google Maps friendly Latin form
        (for example Barcelona,+Spain); do NOT translate location values, URLs or citation ids.
        """.trimIndent()

    fun findPointsOfInterest(
        toolPolicy: String,
        brief: JourneyTravelBrief,
        knowledgeContext: TravelKnowledgeContext,
        maxPois: Int,
    ): String =
        """
        $toolPolicy

        ${languageInstruction(brief)}

        Consider the following travel brief for a journey from ${brief.from} to ${brief.to}.
        ${brief.contribution()}
        Find at most $maxPois points of interest that are relevant to the travel brief and travelers.
        Use mapping tools to consider appropriate order and put a rough date
        range for each point of interest.
        Consider likely weather

        Consider this user-provided travel knowledge when relevant:
        ${knowledgeContext.contribution()}
        """.trimIndent()

    fun researchPointOfInterest(
        toolPolicy: String,
        brief: JourneyTravelBrief,
        poi: PointOfInterest,
        knowledgeContext: TravelKnowledgeContext,
    ): String =
        """
        $toolPolicy

        ${languageInstruction(brief)}

        Research the following point of interest.
        Consider interesting stories about art and culture and famous people.
        Your audience: ${brief.brief}
        Dates to consider: ${brief.departureDate} to ${brief.returnDate}
        If any particularly important events are happening here during this time, mention them
        and list specific dates.
        Also consider likely weather.
        <point-of-interest-to-research>
        ${poi.name}
        ${poi.description}
        ${poi.location}
        Date: from ${poi.fromDate} to: ${poi.toDate}
        </point-of-interest-to-research>
        Use the image search tool to find images of the point of interest.

        User-provided travel knowledge that may be relevant:
        ${knowledgeContext.contribution()}
        """.trimIndent()

    /**
     * Shared, compressed point-of-interest research used by both planner calls. Research is
     * truncated so the proposal prompt stays small — cheaper, faster, less likely to overflow.
     */
    fun poiSummary(poiFindings: PointOfInterestFindings, researchSummaryCharacters: Int): String =
        poiFindings.pointsOfInterest.joinToString("\n\n") { finding ->
            val research = finding.research.take(researchSummaryCharacters)
            val links = finding.links.take(2).joinToString("; ") { "${it.summary}: ${it.url}" }
            val images = finding.imageLinks.take(2).joinToString("; ") { it.url }
            """
                ${finding.pointOfInterest.name} (${finding.pointOfInterest.location})
                $research
                Links: $links
                Image URLs (embed only these as images): $images
            """.trimIndent()
        }

    fun planStructure(brief: JourneyTravelBrief, poiSummary: String): String =
        """
        ${languageInstruction(brief)}

        From the travel brief and researched points of interest below, produce the plan's
        STRUCTURE ONLY (no prose, no HTML):
        - a brief, catchy title (no dates)
        - days: one entry for EVERY date from ${brief.departureDate} to ${brief.returnDate},
          each with locationAndCountry in Google Maps friendly Latin form (e.g. Dijon,+France).
          Repeat the same location for consecutive days in the same town. Minimize travel time.
        - imageLinks / videoLinks / pageLinks: ONLY links the researchers provided below
        - countriesVisited

        <brief>${brief.contribution()}</brief>

        Points of interest:
        $poiSummary
        """.trimIndent()

    fun planHtmlBody(
        brief: JourneyTravelBrief,
        days: List<Day>,
        poiSummary: String,
        knowledgeContext: TravelKnowledgeContext,
        wordCount: Int,
        imageWidth: Int,
    ): String =
        """
        ${languageInstruction(brief)}

        Write a detailed, engaging travel itinerary in HTML, $wordCount words or less.
        Use exactly this day-by-day route (do not change locations or dates):
        ${days.joinToString("\n") { "${it.date}: ${it.locationAndCountry}" }}

        Start headings at <h4>, use paragraphs and unordered lists. Recount at least one
        interesting story about a famous person associated with an area. Embed images only
        from the researcher-provided URLs below, max width ${imageWidth}px, each with
        an informative caption and alt text. If user knowledge influences a recommendation,
        cite it inline using [KB:<citationId>] exactly as provided.

        User-provided travel knowledge:
        ${knowledgeContext.contribution()}

        Points of interest research:
        $poiSummary
        """.trimIndent()

    /**
     * Context shared by both repair calls. Keeping it identical across the structure and HTML
     * passes ensures they describe the same repaired trip.
     */
    fun repairContext(
        brief: JourneyTravelBrief,
        knowledgeContext: TravelKnowledgeContext,
        verification: PlanVerificationResult,
        proposedPlan: ProposedTravelPlan,
        poiFindings: PointOfInterestFindings,
    ): String {
        val repairPoiSummary = poiFindings.pointsOfInterest.joinToString("\n") {
            """
                ${it.pointOfInterest.name}
                ${it.research}
                ${it.links.joinToString { link -> "${link.url}: ${link.summary}" }}
            """.trimIndent()
        }
        return """
        ${languageInstruction(brief)}

        The itinerary verifier found blocking issues in the proposed travel plan. Repair it
        before it is shown to the user. Keep the user's trip intent, travelers, destination,
        date range and style; preserve still-valid recommendations and citations.

        <brief>${brief.contribution()}</brief>

        User-provided travel knowledge:
        ${knowledgeContext.contribution()}

        Structured verifier issues:
        ${verification.contribution()}

        Original days:
        ${proposedPlan.days.joinToString("\n") { "${it.date}: ${it.locationAndCountry}" }}

        Relevant point-of-interest research:
        $repairPoiSummary
        """.trimIndent()
    }

    fun repairStructure(repairContext: String, brief: JourneyTravelBrief): String =
        """
        $repairContext

        Produce the repaired plan's STRUCTURE ONLY (no prose, no HTML):
        - a brief, catchy title (no dates)
        - days: one entry for EVERY date from ${brief.departureDate} to ${brief.returnDate},
          each with a non-empty locationAndCountry in Google Maps friendly Latin form (e.g. Dijon,+France).
          Repeat the same location for consecutive days in the same town. Minimize travel time.
        - imageLinks / videoLinks / pageLinks: drop invalid URLs, keep only valid ones
        - countriesVisited
        """.trimIndent()

    fun repairHtmlBody(
        repairContext: String,
        repairedDays: List<Day>,
        proposedPlan: ProposedTravelPlan,
        wordCount: Int,
    ): String =
        """
        $repairContext

        Rewrite the travel itinerary in HTML to fix the issues above, $wordCount words or less.
        Use exactly this day-by-day route (do not change locations or dates):
        ${repairedDays.joinToString("\n") { "${it.date}: ${it.locationAndCountry}" }}

        Start headings at <h4>, use paragraphs and unordered lists. Remove or replace invalid URLs.
        If user knowledge influences a recommendation, cite it inline using [KB:<citationId>] exactly as provided.

        Original plan to repair (do not restate verbatim):
        ${proposedPlan.plan}
        """.trimIndent()
}
