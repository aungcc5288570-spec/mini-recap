app/src/mpackage com.example.model

import android.graphics.Bitmap
import android.net.Uri

const val MAX_VIDEO_DURATION_MS = 180_000L

enum class InputMode {
    FILE,
    URL,
    TEXT,
}

enum class AppLanguage(val code: String) {
    MM("MM"),
    EN("EN"),
}

enum class DashboardTab {
    HOME,
    TOOLS,
    WALLET,
    PROJECTS,
    ACCOUNT,
}

enum class DashboardTool(
    val id: String,
    val title: String,
    val subtitleMm: String,
    val subtitleEn: String,
    val defaultInputMode: InputMode,
) {
    RECAP(
        id = "recap",
        title = "Recap",
        subtitleMm = "မူရင်းဇာတ်လမ်း အနှစ်ချုပ်",
        subtitleEn = "Original Story Summary",
        defaultInputMode = InputMode.FILE,
    ),
    RECAP_TO_RECAP(
        id = "recap_to_recap",
        title = "Recap to Recap",
        subtitleMm = "ရှိပြီးသား Recap ပြန်ဖန်တီး",
        subtitleEn = "Remix Existing Recap",
        defaultInputMode = InputMode.TEXT,
    ),
    DUBBING(
        id = "dubbing",
        title = "Dubbing",
        subtitleMm = "အသံအစားထိုး",
        subtitleEn = "Burmese Voice Dubbing",
        defaultInputMode = InputMode.TEXT,
    ),
    SUBTITLES(
        id = "subtitles",
        title = "Subtitles",
        subtitleMm = "ဗီဒီယိုစာတန်း",
        subtitleEn = "Video Captions",
        defaultInputMode = InputMode.FILE,
    ),
    SRT(
        id = "srt",
        title = "SRT",
        subtitleMm = "စာတန်းဖိုင်",
        subtitleEn = "Subtitle File (.srt)",
        defaultInputMode = InputMode.TEXT,
    ),
    DOWNLOAD(
        id = "download",
        title = "Download",
        subtitleMm = "Video / Audio",
        subtitleEn = "Video / Audio",
        defaultInputMode = InputMode.URL,
    ),
    NEWS_VIDEO(
        id = "news_video",
        title = "သတင်း ဗီဒီယို",
        subtitleMm = "သတင်း Link / စာသား -> မြန်မာ သတင်းဗီဒီယို",
        subtitleEn = "News Link / Text -> Burmese News Video",
        defaultInputMode = InputMode.TEXT,
    ),
}

enum class BurmeseVoiceProfile(
    val shortBurmeseName: String,
    val defaultPitch: Float,
    val defaultSpeechRate: Float,
) {
    THIHA(
        shortBurmeseName = "သီဟ",
        defaultPitch = 0.85f,
        defaultSpeechRate = 0.96f,
    ),
    NILA(
        shortBurmeseName = "နီလာ",
        defaultPitch = 1.15f,
        defaultSpeechRate = 1.00f,
    ),
}

data class TtsPlaybackState(
    val isReady: Boolean = false,
    val isUnavailable: Boolean = false,
    val isSpeaking: Boolean = false,
    val activeParagraphIndex: Int? = null,
    val totalParagraphs: Int = 0,
    val hasBurmeseVoice: Boolean = true,
    val statusNotice: String? = null,
)

data class SelectedVideoInfo(
    val uri: Uri,
    val displayName: String,
    val durationMs: Long,
    val fileSizeBytes: Long?,
    val width: Int?,
    val height: Int?,
    val thumbnail: Bitmap?,
)

enum class ProcessingStage(val stepNumber: Int) {
    EXTRACTING_AUDIO(1),
    TRANSCRIBING(2),
    WRITING_BURMESE_SCRIPT(3),
    BUILDING_SUBTITLES(4),
}

data class SrtCue(
    val index: Int,
    val startMs: Long,
    val endMs: Long,
    val text: String,
)

data class RecapResult(
    val sourceTitle: String,
    val durationMs: Long,
    val paragraphs: List<String>,
    val srtCues: List<SrtCue>,
    val srtContent: String,
    val isDemoOutput: Boolean,
    val toolUsed: DashboardTool = DashboardTool.RECAP,
) {
    val fullScript: String
        get() = paragraphs.joinToString(separator = "\n\n")
}

sealed interface RecapRequest {
    data class LocalVideo(
        val info: SelectedVideoInfo,
        val toolMode: DashboardTool = DashboardTool.RECAP,
    ) : RecapRequest

    data class VideoUrl(
        val url: String,
        val toolMode: DashboardTool = DashboardTool.RECAP,
    ) : RecapRequest

    data class TextInput(
        val text: String,
        val toolMode: DashboardTool = DashboardTool.RECAP_TO_RECAP,
    ) : RecapRequest
}

sealed interface RecapUiState {
    data object Idle : RecapUiState
    data class Processing(
        val stage: ProcessingStage,
        val progress: Float,
    ) : RecapUiState

    data class Success(val result: RecapResult) : RecapUiState
    data class Error(val message: String) : RecapUiState
}

data class AutoDisclaimerConfig(
    val enabled: Boolean = true,
    val text: String = DEFAULT_DISCLAIMER_TEXT,
) {
    companion object {
        const val DEFAULT_DISCLAIMER_TEXT =
            "Copyright Disclaimer (Fair Use): ဤဗီဒီယိုသည် မူရင်းဖန်တီးမှုကို ဝေဖန်သုံးသပ်ချက်နှင့် ပညာပေးရည်ရွယ်ချက် (Fair Use) ဖြင့် ပြန်လည်တင်ဆက်ထားသော Mini Recap ဖြစ်ပါသည်။"

        val PRESETS: List<Pair<String, String>> = listOf(
            "Fair Use (MM)" to DEFAULT_DISCLAIMER_TEXT,
            "Fair Use (EN)" to "Copyright Disclaimer under Fair Use: Commentary, review, and recap created strictly for educational & transformative entertainment purposes.",
            "News Credit" to "သတင်းအရင်းအမြစ် မှတ်ချက် — ဤဗီဒီယိုသည် အများပြည်သူဆိုင်ရာ သတင်းအချက်အလက်များကို ကိုးကားတင်ဆက်ထားပါသည်။",
        )
    }
}

sealed interface DeviceSaveState {
    data object Idle : DeviceSaveState
    data class Saving(
        val targetLabel: String,
        val progress: Float,
        val bytesCopied: Long,
        val totalBytes: Long,
    ) : DeviceSaveState

    data class Saved(
        val destinationLabel: String,
        val uriString: String,
    ) : DeviceSaveState

    data class Failed(val message: String) : DeviceSaveState
}ain/java/com/example/model/RecapModels.kt
