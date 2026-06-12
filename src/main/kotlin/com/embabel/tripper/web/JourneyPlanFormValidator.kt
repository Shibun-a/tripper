package com.embabel.tripper.web

import com.embabel.tripper.web.JourneyHtmxController.JourneyPlanForm
import org.springframework.context.MessageSource
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * Server-side validation for the journey form. The browser's `required` attributes are a
 * convenience only: a planning run spends real money on LLM and tool calls, so invalid input
 * must be rejected before an agent process is created — not discovered by the verifier after
 * the spend.
 */
class JourneyPlanFormValidator(
    private val maxTripDays: Int,
    private val maxTravelers: Int,
    private val maxBriefCharacters: Int,
    private val messageSource: MessageSource,
) {

    /** Returns localized error messages; empty means the form is safe to plan. */
    fun validate(form: JourneyPlanForm, bindingFailed: Boolean, locale: Locale): List<String> {
        fun msg(key: String, vararg args: Any): String =
            messageSource.getMessage(key, arrayOf(*args), locale)

        val errors = mutableListOf<String>()
        if (bindingFailed) {
            errors.add(msg("form.error.invalidInput"))
        }
        if (form.from.isBlank()) {
            errors.add(msg("form.error.fromRequired"))
        }
        if (form.to.isBlank()) {
            errors.add(msg("form.error.toRequired"))
        }
        if (form.transportPreference.isBlank()) {
            errors.add(msg("form.error.transportRequired"))
        }
        if (form.returnDate.isBefore(form.departureDate)) {
            errors.add(msg("form.error.dateOrder"))
        } else {
            val tripDays = ChronoUnit.DAYS.between(form.departureDate, form.returnDate) + 1
            if (tripDays > maxTripDays) {
                errors.add(msg("form.error.tripTooLong", maxTripDays))
            }
        }
        if (form.dailyBudget <= 0.0) {
            errors.add(msg("form.error.budgetPositive"))
        }
        if (form.travelers.isEmpty() || form.travelers.any { it.name.isBlank() }) {
            errors.add(msg("form.error.travelerName"))
        }
        if (form.travelers.size > maxTravelers) {
            errors.add(msg("form.error.tooManyTravelers", maxTravelers))
        }
        if (form.brief.length > maxBriefCharacters) {
            errors.add(msg("form.error.briefTooLong", maxBriefCharacters))
        }
        return errors
    }
}
