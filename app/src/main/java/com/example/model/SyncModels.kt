package com.example.model

enum class PacingMismatchStrategy {
    FIT_TO_TIMELINE,
    EXPAND_SUBTITLES,
    TRIM_PAUSE,
}

enum class SegmentSyncStatus {
    ALIGNED,
    PADDED,
    SPEED_SCALED,
    CUE_RETIMED,
    FADE_CAPPED,
}

data class AudioVideoSyncConfig(
    val voiceProfile: BurmeseVoiceProfile = BurmeseVoiceProfile.THIHA,
    val pacingStrategy: PacingMismatchStrategy = PacingMismatchStrategy.FIT_TO_TIMELINE,
    val duckOriginalAudio: Boolean = true,
    val originalAudioGain: Float = 1.0f,
    val duckedOriginalGain: Float = 0.18f,
    val voiceOverGain: Float = 1.0f,
    val timelineOffsetMs: Long = 0L,
    val includeSubtitleTrack: Boolean = true,
)

data class SyncedSegmentPlan(
    val cueIndex: Int,
    val text: String,
    val targetStartMs: Long,
    val targetEndMs: Long,
    val syncedStartMs: Long,
    val syncedEndMs: Long,
    val measuredSpeechMs: Long,
    val appliedPlaybackRate: Float,
    val silencePadMs: Long,
    val driftDeltaMs: Long,
    val status: SegmentSyncStatus,
)

data class SyncTimelineReport(
    val segments: List<SyncedSegmentPlan>,
    val syncedCues: List<SrtCue>,
    val syncedSrtContent: String,
    val totalTimelineMs: Long,
    val maxDriftMs: Long,
    val mismatchedCount: Int,
)

sealed interface MuxExportState {
    data object Idle : MuxExportState
    data class Preparing(
        val stepLabel: String,
        val progress: Float,
    ) : MuxExportState

    data class Muxing(
        val stepLabel: String,
        val progress: Float,
    ) : MuxExportState

    data class Ready(
        val outputFilePath: String,
        val syncedSrtContent: String,
        val report: SyncTimelineReport,
        val hasSubtitleTrackInMp4: Boolean,
    ) : MuxExportState

    data class Error(val message: String) : MuxExportState
}
