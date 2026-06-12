package io.github.shibuna.tripsmith.web

import io.github.shibuna.tripsmith.web.JourneyHtmxController.JourneyPlanForm
import io.github.shibuna.tripsmith.web.JourneyHtmxController.TravelerForm
import org.junit.jupiter.api.Test
import org.springframework.context.support.ResourceBundleMessageSource
import java.time.LocalDate
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Uses the real message bundles, so these tests also prove every error key exists in
 * messages.properties (and resolves for the Chinese locale).
 */
class JourneyPlanFormValidatorTest {

    private val messageSource = ResourceBundleMessageSource().apply {
        setBasename("messages")
        setDefaultEncoding("UTF-8")
        setFallbackToSystemLocale(false)
    }

    private val validator = JourneyPlanFormValidator(
        maxTripDays = 30,
        maxTravelers = 8,
        maxBriefCharacters = 4000,
        messageSource = messageSource,
    )

    private fun validForm() = JourneyPlanForm(
        from = "Barcelona",
        to = "Bordeaux",
        transportPreference = "driving",
        brief = "Relaxed road trip.",
        departureDate = LocalDate.of(2026, 7, 1),
        returnDate = LocalDate.of(2026, 7, 10),
        dailyBudget = 200.0,
        travelers = mutableListOf(TravelerForm(name = "Ingrid", about = "history")),
    )

    @Test
    fun `valid form produces no errors`() {
        assertEquals(emptyList(), validator.validate(validForm(), bindingFailed = false, locale = Locale.ENGLISH))
    }

    @Test
    fun `rejects return date before departure`() {
        val form = validForm().copy(returnDate = LocalDate.of(2026, 6, 30))
        val errors = validator.validate(form, bindingFailed = false, locale = Locale.ENGLISH)
        assertEquals(listOf("Return date must not be before the departure date."), errors)
    }

    @Test
    fun `rejects trips longer than the configured cap`() {
        val form = validForm().copy(returnDate = LocalDate.of(2026, 8, 15))
        val errors = validator.validate(form, bindingFailed = false, locale = Locale.ENGLISH)
        assertEquals(listOf("Trips are limited to 30 days."), errors)
    }

    @Test
    fun `rejects non-positive budget blank locations and unnamed travelers`() {
        val form = validForm().copy(
            from = " ",
            dailyBudget = 0.0,
            travelers = mutableListOf(TravelerForm(name = "", about = "")),
        )
        val errors = validator.validate(form, bindingFailed = false, locale = Locale.ENGLISH)
        assertEquals(3, errors.size)
        assertTrue(errors.any { it.contains("Origin") })
        assertTrue(errors.any { it.contains("budget") })
        assertTrue(errors.any { it.contains("traveler needs a name") })
    }

    @Test
    fun `rejects too many travelers and oversized briefs`() {
        val form = validForm().copy(
            brief = "x".repeat(4001),
            travelers = MutableList(9) { TravelerForm(name = "T$it", about = "") },
        )
        val errors = validator.validate(form, bindingFailed = false, locale = Locale.ENGLISH)
        assertEquals(2, errors.size)
    }

    @Test
    fun `reports binding failures and resolves chinese messages`() {
        val errors = validator.validate(validForm(), bindingFailed = true, locale = Locale.SIMPLIFIED_CHINESE)
        assertEquals(listOf("部分字段无法解析，请检查日期和数字。"), errors)
    }
}
