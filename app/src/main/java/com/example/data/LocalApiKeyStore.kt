package com.example.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ApiKeySource {
    LOCAL_USER,
    BUILD_CONFIG,
    NONE,
}

enum class AiProvider(val displayName: String) {
    GEMINI("Google Gemini"),
    GROQ("Groq (Whisper + LLM)"),
}

enum class GeminiModelOption(
    val modelId: String,
    val displayName: String,
) {
    FLASH_2_5("gemini-2.5-flash", "Gemini 2.5 Flash"),
    PRO_2_5("gemini-2.5-pro", "Gemini 2.5 Pro"),
}

enum class GroqWhisperModel(
    val modelId: String,
    val displayName: String,
    val speedBadge: String,
) {
    WHISPER_V3_TURBO("whisper-large-v3-turbo", "Whisper Large V3 Turbo", "Ultra Fast"),
    WHISPER_V3("whisper-large-v3", "Whisper Large V3", "High Accuracy"),
}

enum class GroqLlmModel(
    val modelId: String,
    val displayName: String,
) {
    LLAMA_3_3_70B("llama-3.3-70b-versatile", "Llama 3.3 70B Versatile"),
    LLAMA_3_1_8B("llama-3.1-8b-instant", "Llama 3.1 8B Instant"),
}

data class ApiKeyConfigState(
    val hasLocalKey: Boolean = false,
    val maskedPreview: String = "",
    val activeSource: ApiKeySource = ApiKeySource.NONE,
    val selectedProvider: AiProvider = AiProvider.GEMINI,
    val geminiModel: GeminiModelOption = GeminiModelOption.FLASH_2_5,
    val groqWhisperModel: GroqWhisperModel = GroqWhisperModel.WHISPER_V3_TURBO,
    val groqLlmModel: GroqLlmModel = GroqLlmModel.LLAMA_3_3_70B,
    val hasLocalGeminiKey: Boolean = false,
    val geminiMaskedPreview: String = "",
    val geminiSource: ApiKeySource = ApiKeySource.NONE,
    val hasLocalGroqKey: Boolean = false,
    val groqMaskedPreview: String = "",
    val groqSource: ApiKeySource = ApiKeySource.NONE,
) {
    val isConfigured: Boolean
        get() = activeSource != ApiKeySource.NONE

    val activeProviderConfigured: Boolean
        get() = when (selectedProvider) {
            AiProvider.GEMINI -> geminiSource != ApiKeySource.NONE
            AiProvider.GROQ -> groqSource != ApiKeySource.NONE
        }
}

/**
 * Local-first storage interface for multi-provider AI API keys & model choices.
 * Keys saved by the user stay strictly in private on-device storage (MODE_PRIVATE)
 * and take priority over any build-time BuildConfig placeholder or environment key.
 */
interface LocalApiKeyStore {
    val state: StateFlow<ApiKeyConfigState>
    fun getEffectiveApiKey(): String?
    fun getLocalApiKey(): String
    fun saveLocalApiKey(rawKey: String)
    fun clearLocalApiKey()

    fun getEffectiveGeminiKey(): String?
    fun getEffectiveGroqKey(): String?
    fun saveLocalGroqKey(rawKey: String)
    fun clearLocalGroqKey()
    fun selectProvider(provider: AiProvider)
    fun selectGeminiModel(model: GeminiModelOption)
    fun selectGroqWhisperModel(model: GroqWhisperModel)
    fun selectGroqLlmModel(model: GroqLlmModel)
}

class SharedPreferencesApiKeyStore @JvmOverloads constructor(
    context: Context,
    private val buildConfigKey: String = "",
    private val buildConfigGroqKey: String = "",
) : LocalApiKeyStore {

    private val prefs = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE,
    )

    private val _state = MutableStateFlow(computeState())
    override val state: StateFlow<ApiKeyConfigState> = _state.asStateFlow()

    override fun getEffectiveApiKey(): String? = getEffectiveGeminiKey()

    override fun getLocalApiKey(): String {
        return prefs.getString(KEY_GEMINI_API, "").orEmpty().trim()
    }

    override fun saveLocalApiKey(rawKey: String) {
        val cleaned = rawKey.trim()
        if (cleaned.isEmpty()) {
            clearLocalApiKey()
            return
        }
        prefs.edit().putString(KEY_GEMINI_API, cleaned).apply()
        _state.value = computeState()
    }

    override fun clearLocalApiKey() {
        prefs.edit().remove(KEY_GEMINI_API).apply()
        _state.value = computeState()
    }

    override fun getEffectiveGeminiKey(): String? {
        val local = getLocalApiKey()
        if (local.isNotBlank()) return local
        return validBuildConfigKeyOrNull(buildConfigKey, PLACEHOLDER_GEMINI_KEY)
    }

    override fun getEffectiveGroqKey(): String? {
        val local = prefs.getString(KEY_GROQ_API, "").orEmpty().trim()
        if (local.isNotBlank()) return local
        return validBuildConfigKeyOrNull(buildConfigGroqKey, PLACEHOLDER_GROQ_KEY)
    }

    override fun saveLocalGroqKey(rawKey: String) {
        val cleaned = rawKey.trim()
        if (cleaned.isEmpty()) {
            clearLocalGroqKey()
            return
        }
        prefs.edit().putString(KEY_GROQ_API, cleaned).apply()
        _state.value = computeState()
    }

    override fun clearLocalGroqKey() {
        prefs.edit().remove(KEY_GROQ_API).apply()
        _state.value = computeState()
    }

    override fun selectProvider(provider: AiProvider) {
        prefs.edit().putString(KEY_ACTIVE_PROVIDER, provider.name).apply()
        _state.value = computeState()
    }

    override fun selectGeminiModel(model: GeminiModelOption) {
        prefs.edit().putString(KEY_GEMINI_MODEL, model.name).apply()
        _state.value = computeState()
    }

    override fun selectGroqWhisperModel(model: GroqWhisperModel) {
        prefs.edit().putString(KEY_GROQ_WHISPER_MODEL, model.name).apply()
        _state.value = computeState()
    }

    override fun selectGroqLlmModel(model: GroqLlmModel) {
        prefs.edit().putString(KEY_GROQ_LLM_MODEL, model.name).apply()
        _state.value = computeState()
    }

    private fun computeState(): ApiKeyConfigState {
        val provider = prefs.getString(KEY_ACTIVE_PROVIDER, null)
            ?.let { runCatching { AiProvider.valueOf(it) }.getOrNull() }
            ?: AiProvider.GEMINI

        val geminiModel = prefs.getString(KEY_GEMINI_MODEL, null)
            ?.let { runCatching { GeminiModelOption.valueOf(it) }.getOrNull() }
            ?: GeminiModelOption.FLASH_2_5

        val whisperModel = prefs.getString(KEY_GROQ_WHISPER_MODEL, null)
            ?.let { runCatching { GroqWhisperModel.valueOf(it) }.getOrNull() }
            ?: GroqWhisperModel.WHISPER_V3_TURBO

        val groqLlmModel = prefs.getString(KEY_GROQ_LLM_MODEL, null)
            ?.let { runCatching { GroqLlmModel.valueOf(it) }.getOrNull() }
            ?: GroqLlmModel.LLAMA_3_3_70B

        val localGemini = getLocalApiKey()
        val buildGemini = validBuildConfigKeyOrNull(buildConfigKey, PLACEHOLDER_GEMINI_KEY)
        val geminiSource = when {
            localGemini.isNotBlank() -> ApiKeySource.LOCAL_USER
            buildGemini != null -> ApiKeySource.BUILD_CONFIG
            else -> ApiKeySource.NONE
        }
        val activeGemini = localGemini.ifBlank { buildGemini.orEmpty() }

        val localGroq = prefs.getString(KEY_GROQ_API, "").orEmpty().trim()
        val buildGroq = validBuildConfigKeyOrNull(buildConfigGroqKey, PLACEHOLDER_GROQ_KEY)
        val groqSource = when {
            localGroq.isNotBlank() -> ApiKeySource.LOCAL_USER
            buildGroq != null -> ApiKeySource.BUILD_CONFIG
            else -> ApiKeySource.NONE
        }
        val activeGroq = localGroq.ifBlank { buildGroq.orEmpty() }

        val activeIsGroq = provider == AiProvider.GROQ
        return ApiKeyConfigState(
            hasLocalKey = if (activeIsGroq) localGroq.isNotBlank() else localGemini.isNotBlank(),
            maskedPreview = maskKey(if (activeIsGroq) activeGroq else activeGemini),
            activeSource = if (activeIsGroq) groqSource else geminiSource,
            selectedProvider = provider,
            geminiModel = geminiModel,
            groqWhisperModel = whisperModel,
            groqLlmModel = groqLlmModel,
            hasLocalGeminiKey = localGemini.isNotBlank(),
            geminiMaskedPreview = maskKey(activeGemini),
            geminiSource = geminiSource,
            hasLocalGroqKey = localGroq.isNotBlank(),
            groqMaskedPreview = maskKey(activeGroq),
            groqSource = groqSource,
        )
    }

    private fun validBuildConfigKeyOrNull(raw: String, placeholder: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty() || trimmed == placeholder) return null
        return trimmed
    }

    private fun maskKey(key: String): String {
        if (key.isBlank()) return ""
        return if (key.length <= 8) {
            "••••" + key.takeLast(2)
        } else {
            key.take(4) + "••••" + key.takeLast(4)
        }
    }

    companion object {
        private const val PREFS_NAME = "mini_recap_local_api_keys"
        private const val KEY_GEMINI_API = "gemini_api_key_local"
        private const val KEY_GROQ_API = "groq_api_key_local"
        private const val KEY_ACTIVE_PROVIDER = "ai_active_provider"
        private const val KEY_GEMINI_MODEL = "gemini_model_option"
        private const val KEY_GROQ_WHISPER_MODEL = "groq_whisper_model"
        private const val KEY_GROQ_LLM_MODEL = "groq_llm_model"
        private const val PLACEHOLDER_GEMINI_KEY = "MY_GEMINI_API_KEY"
        private const val PLACEHOLDER_GROQ_KEY = "MY_GROQ_API_KEY"
    }
}
