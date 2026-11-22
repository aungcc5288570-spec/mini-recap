package com.example.data

import android.content.Context
import com.example.model.AutoDisclaimerConfig
import com.example.model.RecapResult
import com.example.model.SrtCue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface DisclaimerStore {
    val state: StateFlow<AutoDisclaimerConfig>
    fun setEnabled(enabled: Boolean)
    fun updateText(text: String)
    fun resetDefault()
}

class SharedPreferencesDisclaimerStore(
    context: Context,
) : DisclaimerStore {

    private val prefs = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE,
    )

    private val _state = MutableStateFlow(loadState())
    override val state: StateFlow<AutoDisclaimerConfig> = _state.asStateFlow()

    override fun setEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
        _state.value = _state.value.copy(enabled = enabled)
    }

    override fun updateText(text: String) {
        prefs.edit().putString(KEY_TEXT, text).apply()
        _state.value = _state.value.copy(text = text)
    }

    override fun resetDefault() {
        val defaultText = AutoDisclaimerConfig.DEFAULT_DISCLAIMER_TEXT
        prefs.edit()
            .putBoolean(KEY_ENABLED, true)
            .putString(KEY_TEXT, defaultText)
            .apply()
        _state.value = AutoDisclaimerConfig(enabled = true, text = defaultText)
    }

    private fun loadState(): AutoDisclaimerConfig {
        val enabled = prefs.getBoolean(KEY_ENABLED, true)
        val savedText = prefs.getString(KEY_TEXT, AutoDisclaimerConfig.DEFAULT_DISCLAIMER_TEXT)
            ?: AutoDisclaimerConfig.DEFAULT_DISCLAIMER_TEXT
        return AutoDisclaimerConfig(enabled = enabled, text = savedText)
    }

    companion object {
        private const val PREFS_NAME = "mini_recap_disclaimer_prefs"
        private const val KEY_ENABLED = "auto_disclaimer_enabled"
        private const val KEY_TEXT = "auto_disclaimer_text"
    }
}

object AutoDisclaimerFormatter {

    fun applyToRecapResult(
        base: RecapResult,
        config: AutoDisclaimerConfig,
    ): RecapResult {
        val disclaimerText = config.text.trim()
        if (!config.enabled || disclaimerText.isEmpty()) {
            return base
        }

        val updatedParagraphs = base.paragraphs + disclaimerText
        val lastCueEnd = base.srtCues.maxOfOrNull { it.endMs } ?: base.durationMs
        val disclaimerStartMs = (lastCueEnd + 150L).coerceAtLeast(0L)
        val disclaimerEndMs = disclaimerStartMs + 4_500L
        val disclaimerCue = SrtCue(
            index = (base.srtCues.maxOfOrNull { it.index } ?: 0) + 1,
            startMs = disclaimerStartMs,
            endMs = disclaimerEndMs,
            text = disclaimerText,
        )
        val updatedCues = base.srtCues + disclaimerCue
        return base.copy(
            durationMs = maxOf(base.durationMs, disclaimerEndMs),
            paragraphs = updatedParagraphs,
            srtCues = updatedCues,
            srtContent = SrtFormatter.renderSrt(updatedCues),
        )
    }

    fun appendToCues(
        cues: List<SrtCue>,
        totalDurationMs: Long,
        config: AutoDisclaimerConfig,
    ): List<SrtCue> {
        val disclaimerText = config.text.trim()
        if (!config.enabled || disclaimerText.isEmpty()) return cues
        if (cues.any { it.text == disclaimerText }) return cues
        val lastEnd = cues.maxOfOrNull { it.endMs } ?: totalDurationMs
        val startMs = (lastEnd + 150L).coerceAtLeast(0L)
        val endMs = startMs + 4_500L
        return cues + SrtCue(
            index = (cues.maxOfOrNull { it.index } ?: 0) + 1,
            startMs = startMs,
            endMs = endMs,
            text = disclaimerText,
        )
    }
}
