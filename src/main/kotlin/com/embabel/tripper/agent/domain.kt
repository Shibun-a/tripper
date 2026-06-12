/*
 * Copyright 2024-2025 Embabel Software, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.embabel.tripper.agent

import com.embabel.agent.domain.library.HasContent
import com.embabel.agent.domain.library.InternetResource
import com.embabel.agent.domain.library.InternetResources
import com.embabel.common.ai.prompt.PromptContributor
import com.embabel.tripper.rag.TravelKnowledgeContext
import com.embabel.tripper.verification.PlanVerificationResult
import com.fasterxml.jackson.annotation.JsonPropertyDescription
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import java.net.URLEncoder
import java.time.LocalDate

@JsonDeserialize(`as` = JourneyTravelBrief::class)
sealed interface TravelBrief : PromptContributor {
    val brief: String
    val departureDate: LocalDate
    val returnDate: LocalDate
    val dailyBudget: Double
}

data class JourneyTravelBrief(
    val from: String,
    val to: String,
    val transportPreference: String,
    override val brief: String,
    override val departureDate: LocalDate,
    override val returnDate: LocalDate,
    override val dailyBudget: Double = 200.0,
    @param:JsonPropertyDescription("Language to write the plan's natural-language content in, e.g. 'English' or 'Chinese (Simplified)'")
    val language: String = "English",
) : TravelBrief {

    override fun contribution(): String =
        """
        Journey from $from to $to
        Dates: $departureDate to $returnDate
        Brief: $brief
        Transport preference: $transportPreference
    """.trimIndent()
}

data class Traveler(
    val name: String,
    val about: String,
)

data class Travelers(
    val travelers: List<Traveler>,
) : PromptContributor {

    override fun contribution(): String =
        if (travelers.isEmpty()) "No information could be found about travelers"
        else "${travelers.size} travelers:\n" + travelers.joinToString(separator = "\n") {
            "${it.name}: ${it.about}"
        }
}

data class PointOfInterest(
    val name: String,
    val description: String,
    val location: String,
    val fromDate: LocalDate,
    val toDate: LocalDate,
)

data class ItineraryIdeas(
    val pointsOfInterest: List<PointOfInterest>,
)

data class ResearchedPointOfInterest(
    // Defaults make binding tolerant of models that intermittently omit fields; the caller
    // overwrites pointOfInterest with the known input POI regardless.
    val pointOfInterest: PointOfInterest = PointOfInterest("", "", "", LocalDate.now(), LocalDate.now()),
    val research: String = "",
    override val links: List<InternetResource> = emptyList(),
    @param:JsonPropertyDescription("Links to videos, from YouTube or other")
    val videoLinks: List<InternetResource> = emptyList(),
    @param:JsonPropertyDescription("Links to images. Links must be the images themselves, not just links to them.")
    val imageLinks: List<InternetResource> = emptyList(),
) : InternetResources

data class PointOfInterestFindings(
    val pointsOfInterest: List<ResearchedPointOfInterest>,
)

data class Day(
    val date: LocalDate,
    @param:JsonPropertyDescription("Location where the traveler will stay on this day in Google Maps friendly format 'City,+Country'")
    val locationAndCountry: String,
) {
    /**
     * More readable location name, e.g. "Paris" rather than "Paris,+FR".
     */
    val stayingAt: String = locationAndCountry.split(",").firstOrNull()?.trim() ?: "Unknown location"
}

data class ProposedTravelPlan(
    @param:JsonPropertyDescription("Catchy title appropriate to the travelers and travel brief")
    val title: String,
    @param:JsonPropertyDescription("Detailed travel plan")
    val plan: String,
    @param:JsonPropertyDescription("List of days in the travel plan")
    val days: List<Day>,
    @param:JsonPropertyDescription("Links to images")
    val imageLinks: List<InternetResource>,
    @param:JsonPropertyDescription("Links to videos")
    val videoLinks: List<InternetResource>,
    @param:JsonPropertyDescription("Links to pages with more information about the travel plan")
    val pageLinks: List<InternetResource>,
    @param:JsonPropertyDescription("List of country names that the travelers will visit")
    val countriesVisited: List<String>,
)

/**
 * Structured metadata for a proposed plan, WITHOUT the long HTML body. Generated as a small,
 * JSON-friendly object so that models with weaker JSON handling (e.g. OpenAI-compatible domestic
 * endpoints) can produce it reliably; the HTML body is generated separately as plain text.
 */
data class ProposedTravelPlanMeta(
    @param:JsonPropertyDescription("Catchy title appropriate to the travelers and travel brief, without dates")
    val title: String = "Travel plan",
    @param:JsonPropertyDescription("One entry per travel date with its location in Google Maps form 'City,+Country'")
    val days: List<Day> = emptyList(),
    @param:JsonPropertyDescription("Image links provided by the researchers")
    val imageLinks: List<InternetResource> = emptyList(),
    @param:JsonPropertyDescription("Video links provided by the researchers")
    val videoLinks: List<InternetResource> = emptyList(),
    @param:JsonPropertyDescription("Links to pages with more information")
    val pageLinks: List<InternetResource> = emptyList(),
    @param:JsonPropertyDescription("Country names that the travelers will visit")
    val countriesVisited: List<String> = emptyList(),
)

data class VerifiedTravelPlanProposal(
    val proposal: ProposedTravelPlan,
    val verificationResult: PlanVerificationResult,
) {

    fun isRepaired(): Boolean = verificationResult.isRepaired

    fun repairAttempts(): Int = verificationResult.repairAttempts
}

data class Stay(
    val days: List<Day>,
    val airbnbUrl: String? = null,
) {

    fun stayingAt(): String {
        return days.firstOrNull()?.stayingAt ?: "Unknown location"
    }

    fun locationAndCountry(): String {
        return days.firstOrNull()?.locationAndCountry ?: "Unknown location"
    }
}

/**
 * Ensure every date from start to end has a day, filling gaps with the previous day's
 * location. Models (especially OpenAI-compatible domestic ones) sometimes omit dates, which
 * would otherwise trip the verifier's DATE_GAP check and force an avoidable repair pass.
 */
internal fun completeDays(
    days: List<Day>,
    start: LocalDate,
    end: LocalDate,
    fallbackLocation: String,
): List<Day> {
    val byDate = days.associateBy { it.date }
    var lastLocation = days.firstOrNull()?.locationAndCountry?.takeIf { it.isNotBlank() } ?: fallbackLocation
    val result = mutableListOf<Day>()
    var cursor = start
    while (!cursor.isAfter(end)) {
        val existing = byDate[cursor]
        if (existing != null && existing.locationAndCountry.isNotBlank()) {
            lastLocation = existing.locationAndCountry
            result.add(existing)
        } else {
            result.add(Day(cursor, lastLocation))
        }
        cursor = cursor.plusDays(1)
    }
    return result
}

/**
 * Group days into stays by consecutive runs of the same city. A plain groupBy on city would
 * merge a return visit (e.g. Paris → Lyon → Paris) into a single stay whose dates span the
 * days spent elsewhere, producing a wrong accommodation search window.
 */
fun consecutiveStays(days: List<Day>): List<Stay> {
    val stays = mutableListOf<Stay>()
    var run = mutableListOf<Day>()
    for (day in days.sortedBy { it.date }) {
        if (run.isNotEmpty() && run.last().stayingAt != day.stayingAt) {
            stays.add(Stay(days = run))
            run = mutableListOf()
        }
        run.add(day)
    }
    if (run.isNotEmpty()) {
        stays.add(Stay(days = run))
    }
    return stays
}

/**
 * Note created by an LLM but assembled in code.
 */
data class TravelPlan(
    val brief: JourneyTravelBrief,
    val proposal: ProposedTravelPlan,
    val stays: List<Stay>,
    val travelers: Travelers,
    val knowledgeContext: TravelKnowledgeContext = TravelKnowledgeContext.empty(),
    val verificationResult: PlanVerificationResult = PlanVerificationResult.empty(),
) : HasContent {

    /**
     * Google Maps link for the whole journey. Computed from days.
     * Even good LLMs seem to get map links wrong, so we compute it here.
     */
    val journeyMapUrl: String
        get() {
            val encodedLocations = proposal.days.distinctBy { it.locationAndCountry }.map { day ->
                URLEncoder.encode(day.locationAndCountry, Charsets.UTF_8.name())
            }

            return if (encodedLocations.size == 1) {
                "https://www.google.com/maps/search/?api=1&query=${encodedLocations.first()}"
            } else {
                "https://www.google.com/maps/dir/${encodedLocations.joinToString("/")}"
            }
        }

    override val content: String
        get() = """
            ${proposal.title}
            ${proposal.plan}
            Days: ${proposal.days.joinToString(separator = "\n") { "${it.date} - ${it.stayingAt}" }}
            Map:
            $journeyMapUrl
            Pages:
            ${proposal.pageLinks.joinToString("\n") { "${it.url} - ${it.summary}" }}
            Images:
            ${proposal.imageLinks.joinToString("\n") { "${it.url} - ${it.summary}" }}
            Knowledge sources:
            ${knowledgeContext.hits.joinToString("\n") { "${it.citationId} - ${it.source}" }}
            Verification:
            ${verificationResult.summary}
            ${verificationResult.issues.joinToString("\n") { "${it.severity} ${it.category}: ${it.message}" }}
        """.trimIndent()
}
