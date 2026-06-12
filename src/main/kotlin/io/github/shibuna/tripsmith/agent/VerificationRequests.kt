package io.github.shibuna.tripsmith.agent

import io.github.shibuna.tripsmith.verification.ItineraryDay
import io.github.shibuna.tripsmith.verification.ItineraryLink
import io.github.shibuna.tripsmith.verification.ItineraryStay
import io.github.shibuna.tripsmith.verification.ItineraryVerificationRequest

/** Adapts the agent's Kotlin domain types to the Java verifier's request model. */
internal fun verificationRequest(
    brief: JourneyTravelBrief,
    plan: ProposedTravelPlan,
    stays: List<Stay>,
): ItineraryVerificationRequest {
    val pageLinks = plan.pageLinks.map {
        ItineraryLink("pageLinks", it.url, it.summary)
    }
    val imageLinks = plan.imageLinks.map {
        ItineraryLink("imageLinks", it.url, it.summary)
    }
    val videoLinks = plan.videoLinks.map {
        ItineraryLink("videoLinks", it.url, it.summary)
    }
    val stayModels = stays.map { stay ->
        ItineraryStay(
            stay.days.map { ItineraryDay(it.date, it.locationAndCountry) },
            stay.airbnbUrl,
        )
    }
    return ItineraryVerificationRequest(
        brief.from,
        brief.to,
        brief.transportPreference,
        brief.departureDate,
        brief.returnDate,
        brief.dailyBudget,
        plan.title,
        plan.plan,
        plan.days.map { ItineraryDay(it.date, it.locationAndCountry) },
        pageLinks + imageLinks + videoLinks,
        stayModels,
    )
}
