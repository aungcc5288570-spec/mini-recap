package com.example.model

import android.net.Uri
import androidx.annotation.FontRes
import com.example.R
import kotlin.math.roundToLong

enum class BurmeseSubtitleFont(
    val displayName: String,
    @FontRes val fontResId: Int,
) {
    NOTO_SANS_MYANMAR("NotoSansMyanmar", R.font.noto_sans_myanmar),
    MYANMAR_SANS_PRO("MyanmarSansPro", R.font.padauk),
    NOTO_SERIF_MYANMAR("NotoSerifMyanmar", R.font.noto_serif_myanmar),
}

enum class CanvasAspectRatio(
    val label: String,
    val ratio: Float,
    val description: String,
) {
    RATIO_16_9("16:9", 16f / 9f, "YouTube / Landscape"),
    RATIO_9_16("9:16", 9f / 16f, "Shorts / Reels / TikTok"),
    RATIO_1_1("1:1", 1f, "Square"),
    RATIO_4_5("4:5", 4f / 5f, "Portrait Feed"),
}

enum class TimelineToolPanel {
    CLIPS,
    SUBTITLE_STYLE,
    CANVAS,
    CROP,
}

data class TimelineClip(
    val id: String,
    val label: String,
    val sourceUri: Uri?,
    val sourceStartMs: Long,
    val sourceEndMs: Long,
    val speed: Float = 1.0f,
) {
    val rawDurationMs: Long
        get() = (sourceEndMs - sourceStartMs).coerceAtLeast(250L)

    val effectiveDurationMs: Long
        get() = (rawDurationMs / speed.coerceIn(0.25f, 4.0f)).roundToLong().coerceAtLeast(250L)
}

data class TimelineClipRange(
    val clip: TimelineClip,
    val timelineStartMs: Long,
    val timelineEndMs: Long,
)

data class SubtitleStyleConfig(
    val font: BurmeseSubtitleFont = BurmeseSubtitleFont.NOTO_SANS_MYANMAR,
    val fontSizeSp: Float = 16f,
    val textColorArgb: Long = 0xFFFFFFFF,
    val backgroundColorArgb: Long = 0xCC090711,
    val backgroundCornerRadiusDp: Float = 8f,
    val verticalPositionFraction: Float = 0.85f,
)

data class CropSpec(
    val presetLabel: String = "Original",
    val left: Float = 0f,
    val top: Float = 0f,
    val right: Float = 1f,
    val bottom: Float = 1f,
    val zoomScale: Float = 1.0f,
    val canvasBackgroundArgb: Long = 0xFF090711,
) {
    val widthFraction: Float
        get() = (right - left).coerceIn(0.2f, 1f)

    val heightFraction: Float
        get() = (bottom - top).coerceIn(0.2f, 1f)
}

data class TimelineEditorState(
    val isOpen: Boolean = false,
    val sourceTitle: String = "",
    val clips: List<TimelineClip> = emptyList(),
    val selectedClipId: String? = null,
    val playheadMs: Long = 0L,
    val isPlaying: Boolean = false,
    val activeToolPanel: TimelineToolPanel = TimelineToolPanel.CLIPS,
    val subtitleCues: List<SrtCue> = emptyList(),
    val subtitleStyle: SubtitleStyleConfig = SubtitleStyleConfig(),
    val canvasRatio: CanvasAspectRatio = CanvasAspectRatio.RATIO_16_9,
    val cropSpec: CropSpec = CropSpec(),
    val isExporting: Boolean = false,
    val exportProgress: Float = 0f,
    val exportedFilePath: String? = null,
    val exportError: String? = null,
    val disclaimerConfig: AutoDisclaimerConfig = AutoDisclaimerConfig(),
    val saveState: DeviceSaveState = DeviceSaveState.Idle,
) {
    val totalDurationMs: Long
        get() = clips.sumOf { it.effectiveDurationMs }
}
