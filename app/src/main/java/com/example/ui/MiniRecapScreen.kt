package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.R
import com.example.model.AppLanguage
import com.example.model.DashboardTab
import com.example.model.DashboardTool
import com.example.model.RecapUiState
import com.example.ui.components.AccountTab
import com.example.ui.components.DashboardBottomNavBar
import com.example.ui.components.DashboardHomeSection
import com.example.ui.components.HeaderBanner
import com.example.ui.components.ProcessingSection
import com.example.ui.components.ProjectsTab
import com.example.ui.components.ResultSection
import com.example.ui.components.ToolsDirectoryTab
import com.example.ui.components.UploadSection
import com.example.ui.components.VideoTimelineEditorScreen
import com.example.ui.components.WalletTab
import com.example.ui.components.WatchlistBottomSheet
import com.example.ui.components.toolIcon
import com.example.ui.theme.Gold300
import com.example.ui.theme.Gold400
import com.example.ui.theme.PureWhite
import com.example.ui.theme.PurpleBorder
import com.example.ui.theme.PurpleCardElevated
import com.example.ui.theme.PurpleDeepBgBottom
import com.example.ui.theme.PurpleDeepBgMid
import com.example.ui.theme.PurpleDeepBgTop
import com.example.ui.theme.PurpleTintDark
import com.example.ui.theme.Slate400

@Composable
fun MiniRecapScreen(
    viewModel: MiniRecapViewModel = viewModel(),
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollState = rememberScrollState()

    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val activeTool by viewModel.activeTool.collectAsStateWithLifecycle()
    val selectedServer by viewModel.selectedServer.collectAsStateWithLifecycle()
    val appLanguage by viewModel.appLanguage.collectAsStateWithLifecycle()
    val inputMode by viewModel.inputMode.collectAsStateWithLifecycle()
    val videoUrl by viewModel.videoUrl.collectAsStateWithLifecycle()
    val textInput by viewModel.textInput.collectAsStateWithLifecycle()
    val selectedVideo by viewModel.selectedVideo.collectAsStateWithLifecycle()
    val isInspectingVideo by viewModel.isInspectingVideo.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val showSrtPreview by viewModel.showSrtPreview.collectAsStateWithLifecycle()
    val selectedVoice by viewModel.selectedVoice.collectAsStateWithLifecycle()
    val ttsState by viewModel.ttsState.collectAsStateWithLifecycle()
    val apiKeyState by viewModel.apiKeyState.collectAsStateWithLifecycle()
    val disclaimerConfig by viewModel.disclaimerConfig.collectAsStateWithLifecycle()
    val syncConfig by viewModel.syncConfig.collectAsStateWithLifecycle()
    val syncReport by viewModel.syncReport.collectAsStateWithLifecycle()
    val muxExportState by viewModel.muxExportState.collectAsStateWithLifecycle()
    val muxSaveState by viewModel.muxSaveState.collectAsStateWithLifecycle()
    val syncedVoiceWavPath by viewModel.syncedVoiceWavPath.collectAsStateWithLifecycle()
    val playheadMs by viewModel.playheadMs.collectAsStateWithLifecycle()
    val isTimelinePlaying by viewModel.isTimelinePlaying.collectAsStateWithLifecycle()
    val activeSyncedCue by viewModel.activeSyncedCue.collectAsStateWithLifecycle()
    val timelineEditorState by viewModel.timelineEditorState.collectAsStateWithLifecycle()
    val watchlistNotes by viewModel.watchlistNotes.collectAsStateWithLifecycle()
    val isCurrentResultSaved by viewModel.isCurrentResultSaved.collectAsStateWithLifecycle()

    var showWatchlistSheet by rememberSaveable { mutableStateOf(false) }

    val copyToClipboard: (String) -> Unit = { scriptText ->
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText("Mini Recap", scriptText))
        viewModel.notifyCopied()
    }

    LaunchedEffect(Unit) {
        viewModel.snackbarEvents.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    BackHandler(enabled = activeTool != null && selectedTab == DashboardTab.HOME) {
        viewModel.closeToolWorkspace()
    }

    // Zero-permission Android Photo Picker for selecting local videos
    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            if (uri != null) {
                viewModel.onVideoPicked(uri)
            }
        },
    )

    // Storage Access Framework document creator for saving .srt subtitles
    val createSrtLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/x-subrip"),
        onResult = { uri ->
            if (uri != null) {
                viewModel.saveSrtToUri(uri)
            }
        },
    )

    // Storage Access Framework document creator for saving muxed .mp4 video
    val createMp4Launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("video/mp4"),
        onResult = { uri ->
            if (uri != null) {
                viewModel.saveMuxedVideoToUri(uri)
            }
        },
    )

    // Storage Access Framework document creator for saving Timeline Editor exported .mp4
    val createTimelineMp4Launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("video/mp4"),
        onResult = { uri ->
            if (uri != null) {
                viewModel.saveExportedTimelineToUri(uri)
            }
        },
    )

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        },
        bottomBar = {
            DashboardBottomNavBar(
                selectedTab = selectedTab,
                onSelectTab = { tab ->
                    viewModel.selectTab(tab)
                },
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            PurpleDeepBgTop,
                            PurpleDeepBgMid,
                            PurpleDeepBgBottom,
                        )
                    )
                )
                .padding(innerPadding),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                HeaderBanner(
                    selectedServer = selectedServer,
                    appLanguage = appLanguage,
                    onSelectServer = viewModel::selectServer,
                    onToggleLanguage = viewModel::setLanguage,
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 680.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        when (selectedTab) {
                            DashboardTab.HOME -> {
                                val currentTool = activeTool
                                if (currentTool == null) {
                                    DashboardHomeSection(
                                        appLanguage = appLanguage,
                                        onToolSelected = viewModel::openTool,
                                        apiKeyState = apiKeyState,
                                        onOpenApiKeySettings = { viewModel.selectTab(DashboardTab.ACCOUNT) },
                                        onOpenTimelineEditor = viewModel::openTimelineEditor,
                                    )
                                } else {
                                    ActiveToolHeaderBar(
                                        tool = currentTool,
                                        appLanguage = appLanguage,
                                        onBackClick = viewModel::closeToolWorkspace,
                                    )

                                    UploadSection(
                                        inputMode = inputMode,
                                        selectedVideo = selectedVideo,
                                        videoUrl = videoUrl,
                                        textInput = textInput,
                                        isInspectingVideo = isInspectingVideo,
                                        isProcessing = uiState is RecapUiState.Processing,
                                        errorMessage = (uiState as? RecapUiState.Error)?.message,
                                        onModeChange = viewModel::setInputMode,
                                        onPickVideoClick = {
                                            videoPickerLauncher.launch(
                                                PickVisualMediaRequest(
                                                    ActivityResultContracts.PickVisualMedia.VideoOnly
                                                )
                                            )
                                        },
                                        onClearVideoClick = viewModel::clearSelectedVideo,
                                        onUrlChange = viewModel::updateVideoUrl,
                                        onTextChange = viewModel::updateTextInput,
                                        onSubmitRecap = viewModel::startRecap,
                                    )

                                    ProcessingSection(uiState = uiState)

                                    ResultSection(
                                        result = (uiState as? RecapUiState.Success)?.result,
                                        selectedVideo = selectedVideo,
                                        selectedVoice = selectedVoice,
                                        ttsState = ttsState,
                                        showSrtPreview = showSrtPreview,
                                        isSavedToWatchlist = isCurrentResultSaved,
                                        watchlistCount = watchlistNotes.size,
                                        syncConfig = syncConfig,
                                        syncReport = syncReport,
                                        muxExportState = muxExportState,
                                        syncedVoiceWavPath = syncedVoiceWavPath,
                                        playheadMs = playheadMs,
                                        isTimelinePlaying = isTimelinePlaying,
                                        activeSyncedCue = activeSyncedCue,
                                        onSelectVoice = viewModel::selectVoice,
                                        onToggleListen = viewModel::toggleListen,
                                        onSelectPacingStrategy = viewModel::updatePacingStrategy,
                                        onToggleDucking = viewModel::toggleAudioDucking,
                                        onDuckedGainChange = viewModel::updateDuckedOriginalGain,
                                        onVoiceGainChange = viewModel::updateVoiceOverGain,
                                        onOffsetChangeMs = viewModel::updateTimelineOffsetMs,
                                        onToggleSubtitleTrack = viewModel::toggleIncludeSubtitleTrack,
                                        onPlayheadChange = viewModel::updatePlayheadMs,
                                        onToggleTimelinePlay = viewModel::setTimelinePlaying,
                                        onTimelineTickCueCheck = viewModel::triggerCueSpeechIfNeeded,
                                        onPrepareSyncedVoice = viewModel::prepareSyncedVoicePreview,
                                        onMuxSyncedMp4 = viewModel::exportSyncedVideoMp4,
                                        onSaveMuxedMp4 = {
                                            createMp4Launcher.launch("mini_recap_synced.mp4")
                                        },
                                        onCopyScript = copyToClipboard,
                                        onDownloadSrt = {
                                            createSrtLauncher.launch("mini_recap_subtitles.srt")
                                        },
                                        onSaveToWatchlist = viewModel::saveCurrentToWatchlist,
                                        onOpenWatchlist = { showWatchlistSheet = true },
                                        onToggleSrtPreview = viewModel::toggleSrtPreview,
                                        onReset = viewModel::resetAll,
                                        disclaimerConfig = disclaimerConfig,
                                        muxSaveState = muxSaveState,
                                        onSaveMuxedToGallery = viewModel::saveMuxedVideoToGallery,
                                        onToggleDisclaimer = viewModel::toggleAutoDisclaimer,
                                        onUpdateDisclaimerText = viewModel::updateDisclaimerText,
                                        onResetDisclaimer = viewModel::resetDisclaimerToDefault,
                                        onOpenApiKeySettings = { viewModel.selectTab(DashboardTab.ACCOUNT) },
                                        onOpenTimelineEditor = viewModel::openTimelineEditor,
                                    )
                                }
                            }

                            DashboardTab.TOOLS -> {
                                ToolsDirectoryTab(
                                    appLanguage = appLanguage,
                                    onSelectTool = viewModel::openTool,
                                )
                            }

                            DashboardTab.WALLET -> {
                                WalletTab(
                                    selectedServer = selectedServer,
                                    watchlistCount = watchlistNotes.size,
                                    appLanguage = appLanguage,
                                    onSwitchToTools = { viewModel.selectTab(DashboardTab.TOOLS) },
                                )
                            }

                            DashboardTab.PROJECTS -> {
                                ProjectsTab(
                                    notes = watchlistNotes,
                                    onCopySummary = copyToClipboard,
                                    onDeleteNote = viewModel::deleteWatchlistNote,
                                )
                            }

                            DashboardTab.ACCOUNT -> {
                                AccountTab(
                                    selectedServer = selectedServer,
                                    appLanguage = appLanguage,
                                    selectedVoice = selectedVoice,
                                    watchlistCount = watchlistNotes.size,
                                    apiKeyState = apiKeyState,
                                    disclaimerConfig = disclaimerConfig,
                                    onSelectServer = viewModel::selectServer,
                                    onSelectLanguage = viewModel::setLanguage,
                                    onSelectVoice = viewModel::selectVoice,
                                    onSelectAiProvider = viewModel::selectAiProvider,
                                    onSelectGeminiModel = viewModel::selectGeminiModel,
                                    onSelectGroqWhisperModel = viewModel::selectGroqWhisperModel,
                                    onSelectGroqLlmModel = viewModel::selectGroqLlmModel,
                                    onSaveApiKey = viewModel::saveLocalApiKey,
                                    onClearApiKey = viewModel::clearLocalApiKey,
                                    onSaveGroqKey = viewModel::saveLocalGroqKey,
                                    onClearGroqKey = viewModel::clearLocalGroqKey,
                                    onToggleDisclaimer = viewModel::toggleAutoDisclaimer,
                                    onUpdateDisclaimerText = viewModel::updateDisclaimerText,
                                    onResetDisclaimer = viewModel::resetDisclaimerToDefault,
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
            }
        }

        if (showWatchlistSheet) {
            WatchlistBottomSheet(
                notes = watchlistNotes,
                onDismiss = { showWatchlistSheet = false },
                onCopySummary = copyToClipboard,
                onDeleteNote = viewModel::deleteWatchlistNote,
            )
        }

        if (timelineEditorState.isOpen) {
            VideoTimelineEditorScreen(
                state = timelineEditorState,
                onClose = viewModel::closeTimelineEditor,
                onSelectToolPanel = viewModel::selectTimelineToolPanel,
                onSelectClip = viewModel::selectTimelineClip,
                onPlayheadChange = viewModel::updateTimelineEditorPlayhead,
                onTogglePlay = viewModel::setTimelineEditorPlaying,
                onSplitAtPlayhead = viewModel::splitTimelineClipAtPlayhead,
                onDeleteSelectedClip = viewModel::deleteSelectedTimelineClip,
                onSelectedClipSpeedChange = viewModel::setSelectedClipSpeed,
                onUpdateSubtitleStyle = viewModel::updateSubtitleStyle,
                onSelectCanvasRatio = viewModel::updateCanvasRatio,
                onSelectCanvasBgColor = viewModel::updateCanvasBackground,
                onApplyCropPreset = viewModel::applyCropPreset,
                onAdjustCropBounds = viewModel::adjustCropBounds,
                onResetCrop = viewModel::resetCrop,
                onExportVideo = viewModel::exportTimelineVideo,
                onSaveExportedVideo = {
                    createTimelineMp4Launcher.launch("mini_recap_timeline_edited.mp4")
                },
                onSaveExportedToGallery = viewModel::saveExportedTimelineToGallery,
                onToggleDisclaimer = viewModel::toggleAutoDisclaimer,
                onUpdateDisclaimerText = viewModel::updateDisclaimerText,
                onResetDisclaimer = viewModel::resetDisclaimerToDefault,
            )
        }
    }
}

@Composable
private fun ActiveToolHeaderBar(
    tool: DashboardTool,
    appLanguage: AppLanguage,
    onBackClick: () -> Unit,
) {
    Surface(
        color = PurpleCardElevated,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, PurpleBorder),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("active_tool_header"),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(PurpleTintDark),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = toolIcon(tool),
                        contentDescription = null,
                        tint = Gold400,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Column {
                    Text(
                        text = tool.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = PureWhite,
                    )
                    Text(
                        text = if (appLanguage == AppLanguage.MM) tool.subtitleMm else tool.subtitleEn,
                        style = MaterialTheme.typography.bodySmall,
                        color = Slate400,
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            TextButton(
                onClick = onBackClick,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("back_to_dashboard_button"),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = null,
                    tint = Gold300,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (appLanguage == AppLanguage.MM) {
                        stringResource(R.string.back_to_dashboard)
                    } else {
                        stringResource(R.string.tool_workspace_back_en)
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = Gold300,
                )
            }
        }
    }
}
