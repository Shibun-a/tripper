package com.embabel.tripper.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.LocaleResolver
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import org.springframework.web.servlet.i18n.LocaleChangeInterceptor
import org.springframework.web.servlet.i18n.SessionLocaleResolver
import java.util.Locale

/**
 * Enables UI language switching between English (default) and Simplified Chinese.
 *
 * The active locale is stored in the HTTP session and can be changed by adding a
 * `?lang=` query parameter to any page, e.g. `?lang=zh_CN` or `?lang=en`.
 * UI strings come from `messages.properties` (English) and `messages_zh_CN.properties`.
 *
 * Note: this only localizes the static UI chrome. The LLM-generated travel plan text
 * is produced in English by the agent prompts; localizing that output is a separate
 * enhancement (pass the locale into the planner prompt).
 */
@Configuration
class I18nConfig : WebMvcConfigurer {

    @Bean
    fun localeResolver(): LocaleResolver {
        val resolver = SessionLocaleResolver()
        resolver.setDefaultLocale(Locale.ENGLISH)
        return resolver
    }

    @Bean
    fun localeChangeInterceptor(): LocaleChangeInterceptor {
        val interceptor = LocaleChangeInterceptor()
        interceptor.paramName = "lang"
        return interceptor
    }

    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(localeChangeInterceptor())
    }
}
