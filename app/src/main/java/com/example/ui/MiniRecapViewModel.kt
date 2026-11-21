package com.example.ui

import android.app.Application
import android.net.Uri
import android.util.Log
import android.util.Patterns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.data.AiProvider
import com.example.data.ApiKeyConfigState
import com.example.data.AutoDisclaimerFormatter
import com.example.data.DeviceMediaSaver
import com.example.data.DisclaimerStore
import com.example.data.GeminiModelOption
import com.example.data.GeminiRecapAiRepository
import com.example.data.GroqLlmModel
import com.example.data.GroqRecapAiRepository
import com.example.data.GroqWhisperModel
import com.example.data.LocalApiKeyStore
import com.example.data.MiniRecapDatabase
import com.example.data.PlaceholderRecapAiRepository
import com.example.data.RecapAiRepository
import com.example.data.RecapTtsController
import com.example.data.RoomWatchlistRepository
import com.example.data.SharedPreferencesApiKeyStore
import com.example.data.SharedPreferencesDisclaimerStore
import com.example.data.SrtFormatter
import com.example.data.SyncTimelinePlanner
import com.example.data.TimelineEditorEngine
import com.example.data.TimelineVideoExporter
import com.example.data.TtsSpeaker
import com.example.data.VideoAudioMuxer
import com.example.data.VideoInspectionResult
import com.example.data.VideoMetadataReader
import com.example.data.WatchlistNoteEntity
import com.example.data.WatchlistRepository
import com.example.model.AppLanguage
import com.example.model.AudioVideoSyncConfig
import com.example.model.AutoDisclaimerConfig
import com.example.model.BurmeseVoiceProfile
import com.example.model.CanvasAspectRatio
import com.example.model.DashboardTab
import com.example.model.DashboardTool
import com.example.model.DeviceSaveState
import com.example.model.InputMode
import com.example.model.MuxExportState
import com.example.model.PacingMismatchStrategy
import com.example.model.RecapRequest
import com.example.model.RecapResult
import com.example.model.RecapUiState
import com.example.model.SelectedVideoInfo
import com.example.model.SrtCue
import com.example.model.SubtitleStyleConfig
import com.example.model.SyncTimelineReport
import com.example.model.TimelineClip
import com.example.model.TimelineEditorState
import com.example.model.TimelineToolPanel
import com.example.model.TtsPlaybackState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val TAG = "MiniRecapViewModel"

class MiniRecapViewModel @JvmOverloads constructor(
    application: Application,
    private val repository: RecapAiRepository? = null,
    private val metadataReader: VideoMetadataReader = VideoMetadataReader(application),
    private val ttsSpeaker: TtsSpeaker = RecapTtsController(application),
    private val watchlistRepository: WatchlistRepository = RoomWatchlistRepository(
        MiniRecapDatabase.getInstance(application).watchlistDao()
    ),
    private val apiKeyStore: LocalApiKeyStore = SharedPreferencesApiKeyStore(application),
    private val videoAudioMuxer: VideoAudioMuxer = VideoAudioMuxer(application),
    private val timelineVideoExporter: TimelineVideoExporter = TimelineVideoExporter(application),
    private val disclaimerStore: DisclaimerStore = SharedPreferencesDisclaimerStore(application),
    private val deviceMediaSaver: DeviceMediaSaver = DeviceMediaSaver(application),
) : AndroidViewModel(application) {

    private val geminiRepository: GeminiRecapAiRepository by lazy {
        GeminiRecapAiRepository(
            context = application,
            apiKeyProvider = { apiKeyStore.getEffectiveGeminiKey().orEmpty() },
            modelProvider = { apiKeyState.value.geminiModel },
        )
    }
    private val groqRepository: GroqRecapAiRepository by lazy {
        GroqRecapAiRepository(
            context = application,
            apiKeyProvider = { apiKeyStore.getEffectiveGroqKey().orEmpty() },
            whisperModelProvider = { apiKeyState.value.groqWhisperModel },
            llmModelProvider = { apiKeyState.value.groqLlmModel },
        )
    }
    private val placeholderRepository: PlaceholderRecapAiRepository by lazy {
        PlaceholderRecapAiRepository()
    }

    private val _selectedTab = MutableStateFlow(DashboardTab.HOME)
    val selectedTab: StateFlow<DashboardTab> = _selectedTab.asStateFlow()

    private val _activeTool = MutableStateFlow<DashboardTool?>(null)
    val activeTool: StateFlow<DashboardTool?> = _activeTool.asStateFlow()

    private val _selectedServer = MutableStateFlow("Server 1")
    val selectedServer: StateFlow<String> = _selectedServer.asStateFlow()

    private val _appLanguage = MutableStateFlow(AppLanguage.MM)
    val appLanguage: StateFlow<AppLanguage> = _appLanguage.asStateFlow()

    private val _inputMode = MutableStateFlow(InputMode.FILE)
    val inputMode: StateFlow<InputMode> = _inputMode.asStateFlow()

    private val _videoUrl = MutableStateFlow("")
    val videoUrl: StateFlow<String> = _videoUrl.asStateFlow()

    private val _textInput = MutableStateFlow("")
    val textInput: StateFlow<String> = _textInput.asStateFlow()

    private val _selectedVideo = MutableStateFlow<SelectedVideoInfo?>(null)
    val selectedVideo: StateFlow<SelectedVideoInfo?> = _selectedVideo.asStateFlow()

    private val _isInspectingVideo = MutableStateFlow(false)
    val isInspectingVideo: StateFlow<Boolean> = _isInspectingVideo.asStateFlow()

    private val _uiState = MutableStateFlow<RecapUiState>(RecapUiState.Idle)
    val uiState: StateFlow<RecapUiState> = _uiState.asStateFlow()

    private val _showSrtPreview = MutableStateFlow(false)
    val showSrtPreview: StateFlow<Boolean> = _showSrtPreview.asStateFlow()

    private val _selectedVoice = MutableStateFlow(BurmeseVoiceProfile.THIHA)
    val selectedVoice: StateFlow<BurmeseVoiceProfile> = _selectedVoice.asStateFlow()

    val ttsState: StateFlow<TtsPlaybackState> = ttsSpeaker.state

    val apiKeyState: StateFlow<ApiKeyConfigState> = apiKeyStore.state

    val disclaimerConfig: StateFlow<AutoDisclaimerConfig> = disclaimerStore.state

    private var _rawSuccessResult: RecapResult? = null

    private val _syncConfig = MutableStateFlow(AudioVideoSyncConfig())
    val syncConfig: StateFlow<AudioVideoSyncConfig> = _syncConfig.asStateFlow()

    private val _measuredCueDurations = MutableStateFlow<Map<Int, Long>>(emptyMap())
    private val _muxExportState = MutableStateFlow<MuxExportState>(MuxExportState.Idle)
    val muxExportState: StateFlow<MuxExportState> = _muxExportState.asStateFlow()

    private val _muxSaveState = MutableStateFlow<DeviceSaveState>(DeviceSaveState.Idle)
    val muxSaveState: StateFlow<DeviceSaveState> = _muxSaveState.asStateFlow()

    private val _syncedVoiceWavPath = MutableStateFlow<String?>(null)
    val syncedVoiceWavPath: StateFlow<String?> = _syncedVoiceWavPath.asStateFlow()

    private val _playheadMs = MutableStateFlow(0L)
    val playheadMs: StateFlow<Long> = _playheadMs.asStateFlow()

    private val _isTimelinePlaying = MutableStateFlow(false)
    val isTimelinePlaying: StateFlow<Boolean> = _isTimelinePlaying.asStateFlow()

    private val _timelineEditorState = MutableStateFlow(
        TimelineEditorState(disclaimerConfig = disclaimerStore.state.value)
    )
    val timelineEditorState: StateFlow<TimelineEditorState> = _timelineEditorState.asStateFlow()

    private var nextClipSequence = 2
    private var lastTriggeredCueIndex: Int? = null

    val syncReport: StateFlow<SyncTimelineReport?> = combine(
        _uiState,
        _syncConfig,
        _measuredCueDurations,
    ) { state, config, measured ->
        val result = (state as? RecapUiState.Success)?.result ?: return@combine null
        SyncTimelinePlanner.planTimeline(
            cues = result.srtCues,
            measuredDurationsMs = measured,
            videoDurationMs = result.durationMs,
            config = config,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = null,
    )

    val activeSyncedCue: StateFlow<SrtCue?> = combine(syncReport, _playheadMs) { report, pos ->
        val cues = report?.syncedCues ?: return@combine null
        SyncTimelinePlanner.activeCueAt(cues, pos)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = null,
    )

    private val _snackbarEvents = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val snackbarEvents: SharedFlow<String> = _snackbarEvents.asSharedFlow()

    val watchlistNotes: StateFlow<List<WatchlistNoteEntity>> = watchlistRepository.allNotes
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = emptyList(),
        )

    val isCurrentResultSaved: StateFlow<Boolean> = combine(_uiState, watchlistNotes) { state, notes ->
        val result = (state as? RecapUiState.Success)?.result ?: return@combine false
        notes.any { it.title == result.sourceTitle && it.summary == result.fullScript }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = false,
    )

    fun selectTab(tab: DashboardTab) {
        _selectedTab.value = tab
    }

    fun openTool(tool: DashboardTool) {
        if (_uiState.value is RecapUiState.Processing) return
        _activeTool.value = tool
        _inputMode.value = tool.defaultInputMode
        _selectedTab.value = DashboardTab.HOME
        if (_uiState.value is RecapUiState.Error) {
            _uiState.value = RecapUiState.Idle
        }
    }

    fun closeToolWorkspace() {
        if (_uiState.value is RecapUiState.Processing) return
        _activeTool.value = null
    }

    fun selectServer(serverName: String) {
        _selectedServer.value = serverName
    }

    fun setLanguage(language: AppLanguage) {
        _appLanguage.value = language
    }

    fun setInputMode(mode: InputMode) {
        if (_uiState.value is RecapUiState.Processing) return
        _inputMode.value = mode
        if (_uiState.value is RecapUiState.Error) {
            _uiState.value = RecapUiState.Idle
        }
    }

    fun updateVideoUrl(url: String) {
        if (_uiState.value is RecapUiState.Processing) return
        _videoUrl.value = url
        if (_uiState.value is RecapUiState.Error) {
            _uiState.value = RecapUiState.Idle
        }
    }

    fun updateTextInput(text: String) {
        if (_uiState.value is RecapUiState.Processing) return
        _textInput.value = text
        if (_uiState.value is RecapUiState.Error) {
            _uiState.value = RecapUiState.Idle
        }
    }

    fun saveLocalApiKey(rawKey: String) {
        apiKeyStore.saveLocalApiKey(rawKey)
        val app = getApplication<Application>()
        if (rawKey.isNotBlank()) {
            _snackbarEvents.tryEmit(app.getString(R.string.msg_api_key_saved))
        } else {
            _snackbarEvents.tryEmit(app.getString(R.string.msg_api_key_cleared))
        }
    }

    fun clearLocalApiKey() {
        apiKeyStore.clearLocalApiKey()
        _snackbarEvents.tryEmit(getApplication<Application>().getString(R.string.msg_api_key_cleared))
    }

    fun saveLocalGroqKey(rawKey: String) {
        apiKeyStore.saveLocalGroqKey(rawKey)
        val app = getApplication<Application>()
        if (rawKey.isNotBlank()) {
            _snackbarEvents.tryEmit(app.getString(R.string.msg_groq_key_saved))
        } else {
            _snackbarEvents.tryEmit(app.getString(R.string.msg_groq_key_cleared))
        }
    }

    fun clearLocalGroqKey() {
        apiKeyStore.clearLocalGroqKey()
        _snackbarEvents.tryEmit(getApplication<Application>().getString(R.string.msg_groq_key_cleared))
    }

    fun selectAiProvider(provider: AiProvider) {
        apiKeyStore.selectProvider(provider)
    }

    fun selectGeminiModel(model: GeminiModelOption) {
        apiKeyStore.selectGeminiModel(model)
    }

    fun selectGroqWhisperModel(model: GroqWhisperModel) {
        apiKeyStore.selectGroqWhisperModel(model)
    }

    fun selectGroqLlmModel(model: GroqLlmModel) {
        apiKeyStore.selectGroqLlmModel(model)
    }

    fun toggleAutoDisclaimer(enabled: Boolean) {
        disclaimerStore.setEnabled(enabled)
        syncDisclaimerAcrossStates()
    }

    fun updateDisclaimerText(text: String) {
        disclaimerStore.updateText(text)
        syncDisclaimerAcrossStates()
    }

    fun resetDisclaimerToDefault() {
        disclaimerStore.resetDefault()
        syncDisclaimerAcrossStates()
    }

    private fun syncDisclaimerAcrossStates() {
        val currentConfig = disclaimerStore.state.value
        _timelineEditorState.update { it.copy(disclaimerConfig = currentConfig) }
        val base = _rawSuccessResult
        if (base != null && _uiState.value is RecapUiState.Success) {
            _uiState.value = RecapUiState.Success(
                AutoDisclaimerFormatter.applyToRecapResult(base, currentConfig)
            )
        }
    }

    fun onVideoPicked(uri: Uri) {
        if (_uiState.value is RecapUiState.Processing) return
        ttsSpeaker.stop()
        _isTimelinePlaying.value = false
        _playheadMs.value = 0L
        _muxExportState.value = MuxExportState.Idle
        _muxSaveState.value = DeviceSaveState.Idle
        _syncedVoiceWavPath.value = null
        _rawSuccessResult = null
        viewModelScope.launch {
            _isInspectingVideo.value = true
            try {
                when (val inspection = metadataReader.inspect(uri)) {
                    is VideoInspectionResult.Valid -> {
                        _selectedVideo.value = inspection.info
                        _uiState.value = RecapUiState.Idle
                    }
                    is VideoInspectionResult.TooLong -> {
                        _selectedVideo.value = null
                        val formatted = SrtFormatter.formatDurationMmSs(inspection.durationMs)
                        val msg = getApplication<Application>().getString(
                            R.string.err_video_too_long,
                            formatted,
                        )
                        _uiState.value = RecapUiState.Error(msg)
                    }
                    VideoInspectionResult.Unreadable -> {
                        _selectedVideo.value = null
                        val msg = getApplication<Application>().getString(R.string.err_video_unreadable)
                        _uiState.value = RecapUiState.Error(msg)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to inspect picked video", e)
                _selectedVideo.value = null
                _uiState.value = RecapUiState.Error(
                    getApplication<Application>().getString(R.string.err_video_unreadable)
                )
            } finally {
                _isInspectingVideo.value = false
            }
        }
    }

    fun clearSelectedVideo() {
        if (_uiState.value is RecapUiState.Processing) return
        ttsSpeaker.stop()
        _isTimelinePlaying.value = false
        _playheadMs.value = 0L
        _selectedVideo.value = null
        _muxExportState.value = MuxExportState.Idle
        _syncedVoiceWavPath.value = null
        if (_uiState.value is RecapUiState.Error) {
            _uiState.value = RecapUiState.Idle
        }
    }

    fun startRecap() {
        if (_uiState.value is RecapUiState.Processing) return
        ttsSpeaker.stop()
        _isTimelinePlaying.value = false
        _playheadMs.value = 0L
        lastTriggeredCueIndex = null
        _measuredCueDurations.value = emptyMap()
        _syncedVoiceWavPath.value = null
        _muxExportState.value = MuxExportState.Idle

        val app = getApplication<Application>()
        val currentTool = _activeTool.value ?: DashboardTool.RECAP

        val request: RecapRequest = when (_inputMode.value) {
            InputMode.FILE -> {
                val video = _selectedVideo.value
                if (video == null) {
                    _uiState.value = RecapUiState.Error(app.getString(R.string.err_no_input))
                    return
                }
                RecapRequest.LocalVideo(video, toolMode = currentTool)
            }
            InputMode.URL -> {
                val trimmed = _videoUrl.value.trim()
                if (trimmed.isEmpty()) {
                    _uiState.value = RecapUiState.Error(app.getString(R.string.err_no_input))
                    return
                }
                if (!isValidHttpUrl(trimmed)) {
                    _uiState.value = RecapUiState.Error(app.getString(R.string.err_invalid_url))
                    return
                }
                RecapRequest.VideoUrl(trimmed, toolMode = currentTool)
            }
            InputMode.TEXT -> {
                val trimmed = _textInput.value.trim()
                if (trimmed.isEmpty()) {
                    _uiState.value = RecapUiState.Error(app.getString(R.string.err_no_text_input))
                    return
                }
                RecapRequest.TextInput(trimmed, toolMode = currentTool)
            }
        }

        val activeRepository = resolveRepository()

        viewModelScope.launch {
            _showSrtPreview.value = false
            try {
                val result = activeRepository.generateBurmeseRecap(request) { stage, progress ->
                    _uiState.value = RecapUiState.Processing(stage = stage, progress = progress)
                }
                _rawSuccessResult = result
                _uiState.value = RecapUiState.Success(
                    AutoDisclaimerFormatter.applyToRecapResult(result, disclaimerConfig.value)
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Recap generation failed", e)
                _rawSuccessResult = null
                _uiState.value = RecapUiState.Error(
                    app.getString(R.string.err_processing_failed)
                )
            }
        }
    }

    private fun resolveRepository(): RecapAiRepository {
        if (repository != null) return repository
        return when (apiKeyState.value.selectedProvider) {
            AiProvider.GROQ -> {
                val groqKey = apiKeyStore.getEffectiveGroqKey()
                if (!groqKey.isNullOrBlank()) groqRepository else placeholderRepository
            }
            AiProvider.GEMINI -> {
                val geminiKey = apiKeyStore.getEffectiveGeminiKey()
                if (!geminiKey.isNullOrBlank()) geminiRepository else placeholderRepository
            }
        }
    }

    fun selectVoice(profile: BurmeseVoiceProfile) {
        if (_selectedVoice.value == profile) return
        _selectedVoice.value = profile
        _syncConfig.update { it.copy(voiceProfile = profile) }
        _syncedVoiceWavPath.value = null
        _muxExportState.value = MuxExportState.Idle
        if (ttsState.value.isSpeaking) {
            val paragraphs = (_uiState.value as? RecapUiState.Success)?.result?.paragraphs ?: return
            ttsSpeaker.speakParagraphs(paragraphs, profile)
        }
    }

    fun toggleListen() {
        val successState = _uiState.value as? RecapUiState.Success ?: return
        if (ttsState.value.isSpeaking) {
            ttsSpeaker.stop()
        } else {
            ttsSpeaker.speakParagraphs(successState.result.paragraphs, _selectedVoice.value)
        }
    }

    fun updatePacingStrategy(strategy: PacingMismatchStrategy) {
        _syncConfig.update { it.copy(pacingStrategy = strategy) }
        _muxExportState.value = MuxExportState.Idle
    }

    fun toggleAudioDucking(enabled: Boolean) {
        _syncConfig.update { it.copy(duckOriginalAudio = enabled) }
        _muxExportState.value = MuxExportState.Idle
    }

    fun updateDuckedOriginalGain(gain: Float) {
        _syncConfig.update { it.copy(duckedOriginalGain = gain.coerceIn(0f, 1f)) }
        _muxExportState.value = MuxExportState.Idle
    }

    fun updateVoiceOverGain(gain: Float) {
        _syncConfig.update { it.copy(voiceOverGain = gain.coerceIn(0.2f, 1.5f)) }
        _muxExportState.value = MuxExportState.Idle
    }

    fun updateTimelineOffsetMs(offsetMs: Long) {
        _syncConfig.update { it.copy(timelineOffsetMs = offsetMs.coerceIn(-1500L, 1500L)) }
        _muxExportState.value = MuxExportState.Idle
    }

    fun toggleIncludeSubtitleTrack(enabled: Boolean) {
        _syncConfig.update { it.copy(includeSubtitleTrack = enabled) }
        _muxExportState.value = MuxExportState.Idle
    }

    fun updatePlayheadMs(positionMs: Long) {
        _playheadMs.value = positionMs.coerceAtLeast(0L)
    }

    fun setTimelinePlaying(playing: Boolean) {
        _isTimelinePlaying.value = playing
        if (!playing) {
            lastTriggeredCueIndex = null
            ttsSpeaker.stop()
        }
    }

    /**
     * When live-previewing without a pre-rendered WAV file, triggers each cue via TTS
     * with its calculated speed multiplier at the exact .srt cue start timestamp.
     */
    fun triggerCueSpeechIfNeeded(positionMs: Long) {
        if (!_isTimelinePlaying.value) return
        if (_syncedVoiceWavPath.value != null) return
        val result = (_uiState.value as? RecapUiState.Success)?.result ?: return
        val report = SyncTimelinePlanner.planTimeline(
            cues = result.srtCues,
            measuredDurationsMs = _measuredCueDurations.value,
            videoDurationMs = result.durationMs,
            config = _syncConfig.value,
        )
        val seg = report.segments.firstOrNull { positionMs in it.syncedStartMs..it.syncedEndMs }
        if (seg != null && seg.cueIndex != lastTriggeredCueIndex) {
            lastTriggeredCueIndex = seg.cueIndex
            val effectiveRate = _syncConfig.value.voiceProfile.defaultSpeechRate * seg.appliedPlaybackRate
            ttsSpeaker.speakCueAligned(
                text = seg.text,
                utteranceId = "timeline_cue_${seg.cueIndex}",
                profile = _syncConfig.value.voiceProfile,
                speechRateOverride = effectiveRate,
            )
        } else if (seg == null) {
            lastTriggeredCueIndex = null
        }
    }

    fun prepareSyncedVoicePreview() {
        val result = (_uiState.value as? RecapUiState.Success)?.result ?: return
        if (_muxExportState.value is MuxExportState.Preparing || _muxExportState.value is MuxExportState.Muxing) return
        val app = getApplication<Application>()

        viewModelScope.launch {
            _muxExportState.value = MuxExportState.Preparing("Synthesizing voice-over segments…", 0.1f)
            runCatching {
                videoAudioMuxer.prepareVoiceTimeline(
                    result = result,
                    config = _syncConfig.value,
                    ttsSpeaker = ttsSpeaker,
                    onProgress = { label, p ->
                        _muxExportState.value = MuxExportState.Preparing(label, p)
                    },
                )
            }.onSuccess { prepared ->
                val measured = prepared.report.segments.associate { it.cueIndex to it.measuredSpeechMs }
                _measuredCueDurations.value = measured
                _syncedVoiceWavPath.value = prepared.wavFile?.absolutePath
                _muxExportState.value = MuxExportState.Idle
                _snackbarEvents.tryEmit(
                    app.getString(R.string.msg_voice_sync_ready, prepared.report.segments.size)
                )
            }.onFailure { e ->
                Log.e(TAG, "Failed to prepare synced voice preview", e)
                _muxExportState.value = MuxExportState.Error(
                    e.message ?: app.getString(R.string.err_processing_failed)
                )
            }
        }
    }

    fun exportSyncedVideoMp4() {
        val result = (_uiState.value as? RecapUiState.Success)?.result ?: return
        val app = getApplication<Application>()
        val sourceVideo = _selectedVideo.value
        if (sourceVideo == null) {
            _snackbarEvents.tryEmit(app.getString(R.string.sync_mux_no_video))
            return
        }
        if (_muxExportState.value is MuxExportState.Preparing || _muxExportState.value is MuxExportState.Muxing) return

        viewModelScope.launch {
            _muxSaveState.value = DeviceSaveState.Idle
            _muxExportState.value = MuxExportState.Muxing("Starting Audio-Video mux pipeline…", 0.05f)
            runCatching {
                videoAudioMuxer.muxSyncedVideo(
                    sourceVideoUri = sourceVideo.uri,
                    result = result,
                    config = _syncConfig.value,
                    ttsSpeaker = ttsSpeaker,
                    onProgress = { label, p ->
                        _muxExportState.value = MuxExportState.Muxing(label, p)
                    },
                )
            }.onSuccess { muxResult ->
                val measured = muxResult.report.segments.associate { it.cueIndex to it.measuredSpeechMs }
                _measuredCueDurations.value = measured
                _muxExportState.value = MuxExportState.Ready(
                    outputFilePath = muxResult.mp4File.absolutePath,
                    syncedSrtContent = muxResult.report.syncedSrtContent,
                    report = muxResult.report,
                    hasSubtitleTrackInMp4 = muxResult.hasSubtitleTrackInMp4,
                )
                _snackbarEvents.tryEmit(app.getString(R.string.msg_mux_ready))
            }.onFailure { e ->
                Log.e(TAG, "MP4 muxing failed", e)
                _muxExportState.value = MuxExportState.Error(
                    e.message ?: app.getString(R.string.err_processing_failed)
                )
            }
        }
    }

    fun saveMuxedVideoToGallery() {
        val ready = _muxExportState.value as? MuxExportState.Ready ?: return
        val app = getApplication<Application>()
        if (_muxSaveState.value is DeviceSaveState.Saving) return

        viewModelScope.launch {
            runCatching {
                deviceMediaSaver.saveVideoToGallery(
                    sourceFile = File(ready.outputFilePath),
                    fileName = "mini_recap_synced_${System.currentTimeMillis()}.mp4",
                    onProgress = { progress, copied, total ->
                        _muxSaveState.value = DeviceSaveState.Saving(
                            targetLabel = app.getString(R.string.save_in_progress_label),
                            progress = progress,
                            bytesCopied = copied,
                            totalBytes = total,
                        )
                    },
                )
            }.onSuccess { location ->
                _muxSaveState.value = DeviceSaveState.Saved(
                    destinationLabel = location.destinationLabel,
                    uriString = location.uriString,
                )
                _snackbarEvents.tryEmit(app.getString(R.string.save_completed_gallery))
            }.onFailure { e ->
                Log.e(TAG, "Failed to save synced MP4 to Gallery", e)
                _muxSaveState.value = DeviceSaveState.Failed(
                    e.message ?: app.getString(R.string.msg_mp4_save_failed)
                )
                _snackbarEvents.tryEmit(app.getString(R.string.msg_mp4_save_failed))
            }
        }
    }

    fun saveMuxedVideoToUri(targetUri: Uri) {
        val ready = _muxExportState.value as? MuxExportState.Ready ?: return
        val app = getApplication<Application>()
        if (_muxSaveState.value is DeviceSaveState.Saving) return

        viewModelScope.launch {
            runCatching {
                deviceMediaSaver.saveVideoToUri(
                    sourceFile = File(ready.outputFilePath),
                    targetUri = targetUri,
                    onProgress = { progress, copied, total ->
                        _muxSaveState.value = DeviceSaveState.Saving(
                            targetLabel = "Saving MP4 to Device Storage…",
                            progress = progress,
                            bytesCopied = copied,
                            totalBytes = total,
                        )
                    },
                )
            }.onSuccess { location ->
                _muxSaveState.value = DeviceSaveState.Saved(
                    destinationLabel = location.destinationLabel,
                    uriString = location.uriString,
                )
                _snackbarEvents.tryEmit(app.getString(R.string.save_completed_files))
            }.onFailure { e ->
                Log.e(TAG, "Failed to save synced MP4 to SAF Uri", e)
                _muxSaveState.value = DeviceSaveState.Failed(
                    e.message ?: app.getString(R.string.msg_mp4_save_failed)
                )
                _snackbarEvents.tryEmit(app.getString(R.string.msg_mp4_save_failed))
            }
        }
    }

    fun toggleSrtPreview() {
        _showSrtPreview.value = !_showSrtPreview.value
    }

    fun notifyCopied() {
        val msg = getApplication<Application>().getString(R.string.msg_copied)
        _snackbarEvents.tryEmit(msg)
    }

    fun saveSrtToUri(targetUri: Uri) {
        val successState = _uiState.value as? RecapUiState.Success ?: return
        val syncedSrt = syncReport.value?.syncedSrtContent ?: successState.result.srtContent
        val app = getApplication<Application>()
        viewModelScope.launch {
            val saved = withContext(Dispatchers.IO) {
                runCatching {
                    app.contentResolver.openOutputStream(targetUri)?.use { stream ->
                        stream.write(syncedSrt.toByteArray(Charsets.UTF_8))
                        stream.flush()
                    } ?: error("OutputStream was null")
                }.isSuccess
            }
            val msg = if (saved) {
                app.getString(R.string.msg_srt_saved)
            } else {
                app.getString(R.string.msg_srt_save_failed)
            }
            _snackbarEvents.tryEmit(msg)
        }
    }

    fun saveCurrentToWatchlist() {
        val successState = _uiState.value as? RecapUiState.Success ?: return
        val result = successState.result
        val app = getApplication<Application>()

        val alreadySaved = watchlistNotes.value.any {
            it.title == result.sourceTitle && it.summary == result.fullScript
        }
        if (alreadySaved) {
            _snackbarEvents.tryEmit(app.getString(R.string.msg_watchlist_already_saved))
            return
        }

        viewModelScope.launch {
            runCatching {
                watchlistRepository.saveNote(
                    title = result.sourceTitle,
                    summary = result.fullScript,
                    durationMs = result.durationMs,
                )
            }.onSuccess {
                _snackbarEvents.tryEmit(app.getString(R.string.msg_watchlist_saved))
            }.onFailure { e ->
                Log.e(TAG, "Failed to save note to watchlist", e)
            }
        }
    }

    fun deleteWatchlistNote(noteId: Long) {
        val app = getApplication<Application>()
        viewModelScope.launch {
            runCatching {
                watchlistRepository.deleteNote(noteId)
            }.onSuccess {
                _snackbarEvents.tryEmit(app.getString(R.string.msg_watchlist_deleted))
            }.onFailure { e ->
                Log.e(TAG, "Failed to delete watchlist note", e)
            }
        }
    }

    // -------------------------------------------------------------------------
    // Video Timeline Editor Actions
    // -------------------------------------------------------------------------

    fun openTimelineEditor() {
        val successResult = (_uiState.value as? RecapUiState.Success)?.result
        val pickedVideo = _selectedVideo.value
        val cues = syncReport.value?.syncedCues
            ?: successResult?.srtCues
            ?: defaultEditorSampleCues()
        val totalMs = (pickedVideo?.durationMs
            ?: successResult?.durationMs
            ?: cues.lastOrNull()?.endMs
            ?: 24_000L).coerceAtLeast(3_000L)
        val sourceTitle = pickedVideo?.displayName
            ?: successResult?.sourceTitle
            ?: "Mini Recap Studio Clip"

        val existing = _timelineEditorState.value
        val initializedClips = if (existing.clips.isNotEmpty() &&
            existing.clips.firstOrNull()?.sourceUri == pickedVideo?.uri
        ) {
            existing.clips
        } else {
            nextClipSequence = 2
            listOf(
                TimelineClip(
                    id = "clip_1",
                    label = "Clip 1",
                    sourceUri = pickedVideo?.uri,
                    sourceStartMs = 0L,
                    sourceEndMs = totalMs,
                    speed = 1.0f,
                )
            )
        }

        _isTimelinePlaying.value = false
        ttsSpeaker.stop()
        _timelineEditorState.value = existing.copy(
            isOpen = true,
            sourceTitle = sourceTitle,
            clips = initializedClips,
            selectedClipId = existing.selectedClipId
                ?.takeIf { id -> initializedClips.any { it.id == id } }
                ?: initializedClips.firstOrNull()?.id,
            playheadMs = existing.playheadMs.coerceIn(0L, initializedClips.sumOf { it.effectiveDurationMs }),
            isPlaying = false,
            subtitleCues = cues,
            exportedFilePath = null,
            exportError = null,
            disclaimerConfig = disclaimerStore.state.value,
            saveState = DeviceSaveState.Idle,
        )
    }

    fun closeTimelineEditor() {
        _timelineEditorState.update { it.copy(isOpen = false, isPlaying = false) }
    }

    fun selectTimelineToolPanel(panel: TimelineToolPanel) {
        _timelineEditorState.update { it.copy(activeToolPanel = panel) }
    }

    fun selectTimelineClip(clipId: String) {
        _timelineEditorState.update { state ->
            if (state.clips.any { it.id == clipId }) {
                state.copy(selectedClipId = clipId)
            } else {
                state
            }
        }
    }

    fun updateTimelineEditorPlayhead(positionMs: Long) {
        _timelineEditorState.update { state ->
            val maxMs = state.totalDurationMs.coerceAtLeast(1L)
            state.copy(playheadMs = positionMs.coerceIn(0L, maxMs))
        }
    }

    fun setTimelineEditorPlaying(playing: Boolean) {
        _timelineEditorState.update { it.copy(isPlaying = playing) }
    }

    fun splitTimelineClipAtPlayhead() {
        val current = _timelineEditorState.value
        val split = TimelineEditorEngine.splitClipAtPlayhead(
            clips = current.clips,
            playheadMs = current.playheadMs,
            nextClipIdGenerator = { "clip_${nextClipSequence++}" },
        )
        if (!split.didSplit) {
            _snackbarEvents.tryEmit(
                getApplication<Application>().getString(R.string.timeline_split_too_short)
            )
            return
        }
        _timelineEditorState.update {
            it.copy(
                clips = split.clips,
                selectedClipId = split.selectedClipId,
                exportedFilePath = null,
                exportError = null,
            )
        }
    }

    fun deleteSelectedTimelineClip() {
        val current = _timelineEditorState.value
        if (current.clips.size <= 1) {
            _snackbarEvents.tryEmit(
                getApplication<Application>().getString(R.string.timeline_delete_last_clip)
            )
            return
        }
        val (remaining, nextSelected) = TimelineEditorEngine.deleteClip(
            clips = current.clips,
            clipId = current.selectedClipId,
        )
        val newTotal = remaining.sumOf { it.effectiveDurationMs }.coerceAtLeast(1L)
        _timelineEditorState.update {
            it.copy(
                clips = remaining,
                selectedClipId = nextSelected,
                playheadMs = it.playheadMs.coerceIn(0L, newTotal),
                isPlaying = false,
                exportedFilePath = null,
                exportError = null,
            )
        }
    }

    fun setSelectedClipSpeed(speed: Float) {
        val snapped = (kotlin.math.round(speed.coerceIn(0.5f, 2.0f) * 20f) / 20f)
        _timelineEditorState.update { state ->
            val updatedClips = TimelineEditorEngine.updateClipSpeed(
                clips = state.clips,
                clipId = state.selectedClipId,
                speed = snapped,
            )
            val newTotal = updatedClips.sumOf { it.effectiveDurationMs }.coerceAtLeast(1L)
            state.copy(
                clips = updatedClips,
                playheadMs = state.playheadMs.coerceIn(0L, newTotal),
                exportedFilePath = null,
                exportError = null,
            )
        }
    }

    fun updateSubtitleStyle(transform: (SubtitleStyleConfig) -> SubtitleStyleConfig) {
        _timelineEditorState.update {
            it.copy(
                subtitleStyle = transform(it.subtitleStyle),
                exportedFilePath = null,
                exportError = null,
            )
        }
    }

    fun updateCanvasRatio(ratio: CanvasAspectRatio) {
        _timelineEditorState.update {
            it.copy(
                canvasRatio = ratio,
                exportedFilePath = null,
                exportError = null,
            )
        }
    }

    fun updateCanvasBackground(colorArgb: Long) {
        _timelineEditorState.update {
            it.copy(
                cropSpec = it.cropSpec.copy(canvasBackgroundArgb = colorArgb),
                exportedFilePath = null,
                exportError = null,
            )
        }
    }

    fun applyCropPreset(presetLabel: String, targetAspect: Float?) {
        _timelineEditorState.update { state ->
            val newCrop = TimelineEditorEngine.buildCenteredCrop(
                presetLabel = presetLabel,
                targetAspect = targetAspect,
                sourceAspect = 16f / 9f,
                existingBgColor = state.cropSpec.canvasBackgroundArgb,
            )
            state.copy(
                cropSpec = newCrop,
                exportedFilePath = null,
                exportError = null,
            )
        }
    }

    fun adjustCropBounds(horizontalTrim: Float, verticalTrim: Float, zoomScale: Float) {
        _timelineEditorState.update { state ->
            state.copy(
                cropSpec = TimelineEditorEngine.adjustCropInsets(
                    current = state.cropSpec,
                    horizontalTrim = horizontalTrim,
                    verticalTrim = verticalTrim,
                    zoomScale = zoomScale,
                ),
                exportedFilePath = null,
                exportError = null,
            )
        }
    }

    fun resetCrop() {
        applyCropPreset(presetLabel = "Original", targetAspect = null)
    }

    fun exportTimelineVideo() {
        val current = _timelineEditorState.value
        val app = getApplication<Application>()
        if (current.clips.none { it.sourceUri != null }) {
            _timelineEditorState.update {
                it.copy(exportError = app.getString(R.string.timeline_export_no_video))
            }
            _snackbarEvents.tryEmit(app.getString(R.string.timeline_export_no_video))
            return
        }
        if (current.isExporting) return

        viewModelScope.launch {
            _timelineEditorState.update {
                it.copy(
                    isExporting = true,
                    exportProgress = 0.05f,
                    exportError = null,
                    saveState = DeviceSaveState.Idle,
                    disclaimerConfig = disclaimerStore.state.value,
                )
            }
            runCatching {
                timelineVideoExporter.exportTimelineVideo(_timelineEditorState.value) { progress ->
                    _timelineEditorState.update { it.copy(exportProgress = progress) }
                }
            }.onSuccess { file ->
                _timelineEditorState.update {
                    it.copy(
                        isExporting = false,
                        exportProgress = 1f,
                        exportedFilePath = file.absolutePath,
                        exportError = null,
                    )
                }
                _snackbarEvents.tryEmit(app.getString(R.string.timeline_export_done))
            }.onFailure { e ->
                Log.e(TAG, "Timeline video export failed", e)
                _timelineEditorState.update {
                    it.copy(
                        isExporting = false,
                        exportError = e.message ?: app.getString(R.string.err_processing_failed),
                    )
                }
            }
        }
    }

    fun saveExportedTimelineToGallery() {
        val path = _timelineEditorState.value.exportedFilePath ?: return
        val app = getApplication<Application>()
        if (_timelineEditorState.value.saveState is DeviceSaveState.Saving) return

        viewModelScope.launch {
            runCatching {
                deviceMediaSaver.saveVideoToGallery(
                    sourceFile = File(path),
                    fileName = "mini_recap_timeline_${System.currentTimeMillis()}.mp4",
                    onProgress = { progress, copied, total ->
                        _timelineEditorState.update {
                            it.copy(
                                saveState = DeviceSaveState.Saving(
                                    targetLabel = app.getString(R.string.save_in_progress_label),
                                    progress = progress,
                                    bytesCopied = copied,
                                    totalBytes = total,
                                )
                            )
                        }
                    },
                )
            }.onSuccess { location ->
                _timelineEditorState.update {
                    it.copy(
                        saveState = DeviceSaveState.Saved(
                            destinationLabel = location.destinationLabel,
                            uriString = location.uriString,
                        )
                    )
                }
                _snackbarEvents.tryEmit(app.getString(R.string.save_completed_gallery))
            }.onFailure { e ->
                Log.e(TAG, "Failed to save edited MP4 to Gallery", e)
                _timelineEditorState.update {
                    it.copy(
                        saveState = DeviceSaveState.Failed(
                            e.message ?: app.getString(R.string.msg_mp4_save_failed)
                        )
                    )
                }
                _snackbarEvents.tryEmit(app.getString(R.string.msg_mp4_save_failed))
            }
        }
    }

    fun saveExportedTimelineToUri(targetUri: Uri) {
        val path = _timelineEditorState.value.exportedFilePath ?: return
        val app = getApplication<Application>()
        if (_timelineEditorState.value.saveState is DeviceSaveState.Saving) return

        viewModelScope.launch {
            runCatching {
                deviceMediaSaver.saveVideoToUri(
                    sourceFile = File(path),
                    targetUri = targetUri,
                    onProgress = { progress, copied, total ->
                        _timelineEditorState.update {
                            it.copy(
                                saveState = DeviceSaveState.Saving(
                                    targetLabel = "Saving MP4 to Device Storage…",
                                    progress = progress,
                                    bytesCopied = copied,
                                    totalBytes = total,
                                )
                            )
                        }
                    },
                )
            }.onSuccess { location ->
                _timelineEditorState.update {
                    it.copy(
                        saveState = DeviceSaveState.Saved(
                            destinationLabel = location.destinationLabel,
                            uriString = location.uriString,
                        )
                    )
                }
                _snackbarEvents.tryEmit(app.getString(R.string.save_completed_files))
            }.onFailure { e ->
                Log.e(TAG, "Failed to save edited MP4 to SAF", e)
                _timelineEditorState.update {
                    it.copy(
                        saveState = DeviceSaveState.Failed(
                            e.message ?: app.getString(R.string.msg_mp4_save_failed)
                        )
                    )
                }
                _snackbarEvents.tryEmit(app.getString(R.string.msg_mp4_save_failed))
            }
        }
    }

    private fun defaultEditorSampleCues(): List<SrtCue> = listOf(
        SrtCue(
            index = 1,
            startMs = 0L,
            endMs = 7_500L,
            text = "ဇာတ်လမ်းအစမှာတော့ မထင်မှတ်ထားတဲ့ လျှို့ဝှက်ချက်ကြီးကို တွေ့ရှိလိုက်ရပါတယ်။",
        ),
        SrtCue(
            index = 2,
            startMs = 8_000L,
            endMs = 15_500L,
            text = "အဓိကဇာတ်ကောင်ဟာ အချိန်မီ တားဆီးနိုင်ဖို့ အပြေးအလွှား ကြိုးစားနေပါတယ်။",
        ),
        SrtCue(
            index = 3,
            startMs = 16_000L,
            endMs = 23_500L,
            text = "နောက်ဆုံးမှာတော့ အမှန်တရားပေါ်ပေါက်သွားပြီး ဇာတ်သိမ်းသွားပါတယ်။",
        ),
    )

    fun resetAll() {
        if (_uiState.value is RecapUiState.Processing) return
        ttsSpeaker.stop()
        _isTimelinePlaying.value = false
        _playheadMs.value = 0L
        _selectedVideo.value = null
        _videoUrl.value = ""
        _showSrtPreview.value = false
        _measuredCueDurations.value = emptyMap()
        _syncedVoiceWavPath.value = null
        _muxExportState.value = MuxExportState.Idle
        _muxSaveState.value = DeviceSaveState.Idle
        _rawSuccessResult = null
        _timelineEditorState.value = TimelineEditorState(disclaimerConfig = disclaimerStore.state.value)
        _uiState.value = RecapUiState.Idle
    }

    override fun onCleared() {
        super.onCleared()
        ttsSpeaker.shutdown()
    }

    private fun isValidHttpUrl(url: String): Boolean {
        val lower = url.lowercase()
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) return false
        return Patterns.WEB_URL.matcher(url).matches()
    }
}
