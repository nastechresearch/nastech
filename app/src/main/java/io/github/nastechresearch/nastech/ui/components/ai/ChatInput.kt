package io.github.nastechresearch.nastech.ui.components.ai

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.content.MediaType
import androidx.compose.foundation.content.ReceiveContentListener
import androidx.compose.foundation.content.consume
import androidx.compose.foundation.content.contentReceiver
import androidx.compose.foundation.content.hasMediaType
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.dokar.sonner.ToastType
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.blurEffect
import dev.chrisbanes.haze.blur.materials.HazeMaterials
import dev.chrisbanes.haze.hazeEffect
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collectLatest
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelAbility
import me.rerere.ai.provider.ModelType
import me.rerere.asr.ASRStatus
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.ArrowDown01
import me.rerere.hugeicons.stroke.ArrowUp01
import me.rerere.hugeicons.stroke.ArrowUp02
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.Camera01
import me.rerere.hugeicons.stroke.File01
import me.rerere.hugeicons.stroke.FullScreen
import me.rerere.hugeicons.stroke.Image02
import me.rerere.hugeicons.stroke.Tick01
import me.rerere.hugeicons.stroke.Zap
import me.rerere.hugeicons.stroke.Voice
import io.github.nastechresearch.nastech.R
import io.github.nastechresearch.nastech.data.ai.tools.LocalToolOption
import io.github.nastechresearch.nastech.data.datastore.GlassSurface
import io.github.nastechresearch.nastech.data.datastore.Settings
import io.github.nastechresearch.nastech.data.datastore.getCurrentAssistant
import io.github.nastechresearch.nastech.data.datastore.getCurrentChatModel
import io.github.nastechresearch.nastech.data.datastore.getQuickMessagesOfAssistant
import io.github.nastechresearch.nastech.data.files.FilesManager
import io.github.nastechresearch.nastech.data.files.SkillManager
import io.github.nastechresearch.nastech.data.files.SkillMetadata
import io.github.nastechresearch.nastech.data.model.Assistant
import io.github.nastechresearch.nastech.data.model.QuickMessage
import io.github.nastechresearch.nastech.ui.components.ai.completion.ChatCompletionContext
import io.github.nastechresearch.nastech.ui.components.ai.completion.ChatCompletionItem
import io.github.nastechresearch.nastech.ui.components.ai.completion.ChatCompletionList
import io.github.nastechresearch.nastech.ui.components.ai.completion.ChatCompletionProvider
import io.github.nastechresearch.nastech.ui.components.ui.KeepScreenOn
import io.github.nastechresearch.nastech.ui.components.ui.permission.PermissionManager
import io.github.nastechresearch.nastech.ui.components.ui.permission.PermissionRecordAudio
import io.github.nastechresearch.nastech.ui.components.ui.permission.rememberPermissionState
import io.github.nastechresearch.nastech.ui.context.LocalASRState
import io.github.nastechresearch.nastech.ui.context.LocalSettings
import io.github.nastechresearch.nastech.ui.context.LocalToaster
import io.github.nastechresearch.nastech.ui.hooks.ChatInputState
import io.github.nastechresearch.nastech.ui.theme.glassSurface
import io.github.nastechresearch.nastech.utils.SoundEffectPlayer
import org.koin.compose.koinInject
import kotlin.time.Duration.Companion.seconds

@Composable
fun ChatInput(
    state: ChatInputState,
    loading: Boolean,
    settings: Settings,
    hazeState: HazeState,
    enableSearch: Boolean,
    onToggleSearch: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    completionProviders: List<ChatCompletionProvider> = emptyList(),
    onUpdateChatModel: (Model) -> Unit,
    onUpdateAssistant: (Assistant) -> Unit,
    onUpdateSearchService: (Int) -> Unit,
    onMoreClick: () -> Unit,
    onCancelClick: () -> Unit,
    onSendClick: () -> Unit,
    onLongSendClick: () -> Unit,
    onQueueClick: () -> Unit,
    onLiveVoiceCommand: (String) -> String,
) {
    val toaster = LocalToaster.current
    val assistant = settings.getCurrentAssistant()
    val hazeTintColor = MaterialTheme.colorScheme.surfaceContainerLow
    val inputGlass = glassSurface(GlassSurface.CHAT_INPUT, hazeTintColor)
    val inputHazeStyle = HazeMaterials.thin(containerColor = inputGlass.container)
    val useInputBlur = settings.displaySetting.enableBlurEffect && inputGlass.enabled && inputGlass.blurEnabled

    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    // 键盘弹出时让底部两角变直角，贴合 IME
    val imeVisible = WindowInsets.isImeVisible
    val containerShape = if (imeVisible) {
        MaterialTheme.shapes.largeIncreased.copy(
            bottomStart = CornerSize(0.dp),
            bottomEnd = CornerSize(0.dp),
        )
        } else {
        RoundedCornerShape(30.dp)
    }

    fun sendMessage() {
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
        if (loading) onCancelClick() else onSendClick()
    }

    fun sendMessageWithoutAnswer() {
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
        if (loading) onCancelClick() else onLongSendClick()
    }

    fun steerGeneration() {
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
        onSendClick()
    }

    fun queueDraft() {
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
        onQueueClick()
    }

    val asr = LocalASRState.current
    DisposableEffect(asr, onLiveVoiceCommand) {
        asr.setLiveCommandHandler(onLiveVoiceCommand)
        onDispose { asr.setLiveCommandHandler(null) }
    }
    val asrState by asr.state.collectAsState()
    val isLiveVoiceCall = asr.isLiveConversation
    val hapticFeedback = LocalHapticFeedback.current
    val soundEffectPlayer: SoundEffectPlayer = koinInject()
    LaunchedEffect(Unit) {
        soundEffectPlayer.preload(R.raw.asr_start, R.raw.asr_stop)
    }
    val asrPermission = rememberPermissionState(PermissionRecordAudio)
    PermissionManager(permissionState = asrPermission)
    var asrBaseText by remember { mutableStateOf("") }
    fun toggleVoiceSession() {
        when (asrState.status) {
            ASRStatus.Listening -> asr.stop()
            ASRStatus.Idle, ASRStatus.Error -> {
                if (!asrPermission.allRequiredPermissionsGranted) {
                    asrPermission.requestPermissions()
                } else {
                    asrBaseText = state.textContent.text.toString()
                    asr.start { transcript ->
                        if (!asr.isLiveConversation) {
                            val spacer = if (asrBaseText.isBlank() || transcript.isBlank()) "" else " "
                            state.setMessageText(asrBaseText + spacer + transcript)
                        }
                    }
                }
            }

            ASRStatus.Connecting, ASRStatus.Stopping -> Unit
        }
    }
    LaunchedEffect(asrState.status) {
        when (asrState.status) {
            ASRStatus.Listening -> {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                soundEffectPlayer.play(R.raw.asr_start)
            }

            ASRStatus.Stopping -> {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.GestureEnd)
                soundEffectPlayer.play(R.raw.asr_stop)
            }

            else -> {}
        }
    }
    LaunchedEffect(asrState.errorMessage) {
        asrState.errorMessage?.takeIf { it.isNotBlank() }?.let { message ->
            toaster.show(message = message, type = ToastType.Error)
        }
    }

    Surface(
        color = Color.Transparent,
    ) {
        Column(
            modifier = modifier
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 8.dp)
                .padding(bottom = if (imeVisible) 0.dp else 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(containerShape)
                    .then(
                        if (useInputBlur) Modifier.hazeEffect(
                            state = hazeState
                        ) {
                            blurEffect {
                                style = inputHazeStyle
                            }
                        }
                        else Modifier
                    ),
                shape = containerShape,
                tonalElevation = 0.dp,
                border = BorderStroke(
                    1.dp,
                    if (inputGlass.enabled) inputGlass.border else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                ),
                color = if (useInputBlur) Color.Transparent else inputGlass.container,
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (state.messageContent.isNotEmpty()) {
                        MediaFileInputRow(state = state)
                    }

                    AnimatedVisibility(
                        visible = isLiveVoiceCall && asrState.isRecording,
                        enter = fadeIn() + scaleIn(),
                        exit = fadeOut() + scaleOut(),
                    ) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            color = if (asrState.isAgentSpeaking) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.secondaryContainer
                            },
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                Text(
                                    text = if (asrState.isAgentSpeaking) "ElevenLabs agent speaking" else "ElevenLabs live call",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (asrState.isAgentSpeaking) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSecondaryContainer
                                    },
                                )
                                val liveTranscript = when {
                                    asrState.agentResponse.isNotBlank() -> asrState.agentResponse
                                    asrState.transcript.isNotBlank() -> asrState.transcript
                                    else -> "Speak naturally. You can interrupt the agent at any time."
                                }
                                Text(
                                    text = liveTranscript,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    color = if (asrState.isAgentSpeaking) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSecondaryContainer
                                    },
                                )
                            }
                        }
                    }

                    var showComposerActions by remember { mutableStateOf(false) }
                    val hasDraft = !state.isEmpty()
                    val voiceReady = (asrState.isAvailable || asrState.isRecording) && !hasDraft
                    val isVoiceActive = asrState.isRecording

                    TextInputRow(
                        state = state,
                        completionProviders = completionProviders,
                        onSendMessage = { sendMessage() },
                        onUpdateAssistant = onUpdateAssistant,
                        leadingAction = {
                            ActionIconButton(onClick = { showComposerActions = true }) {
                                Icon(
                                    imageVector = HugeIcons.Add01,
                                    contentDescription = stringResource(R.string.more_options),
                                )
                            }
                        },
                        trailingAction = {
                            CompactComposerPrimaryAction(
                                loading = loading,
                                hasDraft = hasDraft,
                                voiceReady = voiceReady,
                                isVoiceActive = isVoiceActive,
                                borderColor = if (inputGlass.enabled) {
                                    inputGlass.border
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                                },
                                onClick = {
                                    when {
                                        loading && hasDraft -> steerGeneration()
                                        loading -> sendMessage()
                                        isVoiceActive -> toggleVoiceSession()
                                        hasDraft -> sendMessage()
                                        voiceReady -> toggleVoiceSession()
                                    }
                                },
                                onLongClick = {
                                    when {
                                        hasDraft && loading -> queueDraft()
                                        hasDraft && !isVoiceActive -> sendMessageWithoutAnswer()
                                    }
                                },
                            )
                        },
                    )

                    if (showComposerActions) {
                        CompactComposerActionSheet(
                            assistant = assistant,
                            settings = settings,
                            enableSearch = enableSearch,
                            onDismiss = { showComposerActions = false },
                            onOpenAttachments = {
                                showComposerActions = false
                                onMoreClick()
                            },
                            onUpdateChatModel = onUpdateChatModel,
                            onToggleSearch = onToggleSearch,
                            onUpdateSearchService = onUpdateSearchService,
                            onUpdateAssistant = onUpdateAssistant,
                        )
                    }
                }
            }

        }
    }
}

@Composable
private fun ActionIconButton(
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(42.dp),
        shape = CircleShape,
        tonalElevation = 0.dp,
        color = Color.Transparent,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center
        ) {
            content()
        }
    }
}

@Composable
private fun CompactComposerPrimaryAction(
    loading: Boolean,
    hasDraft: Boolean,
    voiceReady: Boolean,
    isVoiceActive: Boolean,
    borderColor: Color,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val enabled = loading || isVoiceActive || hasDraft || voiceReady
    val containerColor = when {
        loading -> MaterialTheme.colorScheme.errorContainer
        isVoiceActive -> MaterialTheme.colorScheme.secondaryContainer
        hasDraft -> MaterialTheme.colorScheme.primary
        voiceReady -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val contentColor = when {
        loading -> MaterialTheme.colorScheme.onErrorContainer
        isVoiceActive -> MaterialTheme.colorScheme.onSecondaryContainer
        hasDraft -> MaterialTheme.colorScheme.onPrimary
        voiceReady -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    }
    if (loading) KeepScreenOn()
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(42.dp)
            .testTag("chat_primary_action_button")
            .clip(CircleShape)
            .combinedClickable(
                enabled = enabled,
                onClick = onClick,
                onLongClick = onLongClick,
            ),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = CircleShape,
            color = containerColor,
            border = BorderStroke(1.dp, borderColor),
            content = {},
        )
        when {
            loading -> Icon(
                imageVector = HugeIcons.Cancel01,
                contentDescription = stringResource(R.string.stop),
                tint = contentColor,
                modifier = Modifier.size(20.dp),
            )
            isVoiceActive -> Icon(
                imageVector = HugeIcons.Voice,
                contentDescription = "End Nastech Voice call",
                tint = contentColor,
                modifier = Modifier.size(20.dp),
            )
            hasDraft -> Icon(
                imageVector = HugeIcons.ArrowUp02,
                contentDescription = stringResource(R.string.send),
                tint = contentColor,
                modifier = Modifier.size(20.dp),
            )
            else -> Icon(
                imageVector = HugeIcons.Voice,
                contentDescription = "Start Nastech Voice call",
                tint = contentColor,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun CompactComposerActionSheet(
    assistant: Assistant,
    settings: Settings,
    enableSearch: Boolean,
    onDismiss: () -> Unit,
    onOpenAttachments: () -> Unit,
    onUpdateChatModel: (Model) -> Unit,
    onToggleSearch: (Boolean) -> Unit,
    onUpdateSearchService: (Int) -> Unit,
    onUpdateAssistant: (Assistant) -> Unit,
) {
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )
    val chatModel = settings.getCurrentChatModel()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = glassSurface(
            GlassSurface.CHAT_INPUT,
            MaterialTheme.colorScheme.surfaceContainerHigh,
        ).container,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Add to message", style = MaterialTheme.typography.titleMedium)
            CompactComposerActionItem("Camera", "Take a photo", HugeIcons.Camera01, onOpenAttachments)
            CompactComposerActionItem("Photos", "Choose images or video", HugeIcons.Image02, onOpenAttachments)
            CompactComposerActionItem("Files", "Attach a document or audio", HugeIcons.File01, onOpenAttachments)
            CompactComposerActionItem("Plugins", "Open connected tools", HugeIcons.Zap, onOpenAttachments)

            Spacer(Modifier.padding(top = 4.dp))
            Text("Chat controls", style = MaterialTheme.typography.titleMedium)
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    ModelSelector(
                        modelId = assistant.chatModelId ?: settings.chatModelId,
                        providers = settings.providers,
                        onSelect = onUpdateChatModel,
                        type = ModelType.CHAT,
                        onlyIcon = false,
                    )
                    SearchPickerButton(
                        enableSearch = enableSearch,
                        settings = settings,
                        onToggleSearch = onToggleSearch,
                        onUpdateSearchService = onUpdateSearchService,
                        model = chatModel,
                    )
                }
            }
            if (chatModel?.abilities?.contains(ModelAbility.REASONING) == true) {
                ReasoningButton(
                    reasoningLevel = assistant.reasoningLevel,
                    onUpdateReasoningLevel = { level ->
                        onUpdateAssistant(assistant.copy(reasoningLevel = level))
                    },
                    onlyIcon = false,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.padding(bottom = 10.dp))
        }
    }
}

@Composable
private fun CompactComposerActionItem(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.72f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(36.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun TextInputRow(
    state: ChatInputState,
    completionProviders: List<ChatCompletionProvider>,
    onSendMessage: () -> Unit,
    onUpdateAssistant: (Assistant) -> Unit,
    leadingAction: @Composable () -> Unit,
    trailingAction: @Composable () -> Unit,
) {
    val settings = LocalSettings.current
    val filesManager: FilesManager = koinInject()
    val skillManager: SkillManager = koinInject()
    val assistant = settings.getCurrentAssistant()
    val installedSkills = remember { skillManager.listSkills() }
    val quickMessages = remember(settings.quickMessages, assistant.quickMessageIds) {
        settings.getQuickMessagesOfAssistant(assistant)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (state.isEditing()) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = stringResource(R.string.editing))
                    Spacer(Modifier.weight(1f))
                    Icon(
                        imageVector = HugeIcons.Cancel01,
                        contentDescription = stringResource(R.string.cancel_edit),
                        modifier = Modifier.clickable { state.clearInput() }
                    )
                }
            }
        }

        var isFocused by remember { mutableStateOf(false) }
        var isFullScreen by remember { mutableStateOf(false) }
        var completionList by remember { mutableStateOf<ChatCompletionList?>(null) }
        val receiveContentListener = remember(
            settings.displaySetting.pasteLongTextAsFile, settings.displaySetting.pasteLongTextThreshold
        ) {
            ReceiveContentListener { transferableContent ->
                when {
                    transferableContent.hasMediaType(MediaType.Image) -> {
                        transferableContent.consume { item ->
                            val uri = item.uri
                            if (uri != null) {
                                state.addImages(
                                    filesManager.createChatFilesByContents(
                                        listOf(uri)
                                    )
                                )
                            }
                            uri != null
                        }
                    }

                    settings.displaySetting.pasteLongTextAsFile && transferableContent.hasMediaType(MediaType.Text) -> {
                        transferableContent.consume { item ->
                            val text = item.text?.toString()
                            if (text != null && text.length > settings.displaySetting.pasteLongTextThreshold) {
                                val document = filesManager.createChatTextFile(text)
                                state.addFiles(listOf(document))
                                true
                            } else {
                                false
                            }
                        }
                    }

                    else -> transferableContent
                }
            }
        }

        LaunchedEffect(completionProviders, isFocused) {
            if (!isFocused || completionProviders.isEmpty()) {
                completionList = null
                return@LaunchedEffect
            }

            snapshotFlow {
                ChatCompletionContext(
                    text = state.textContent.text.toString(),
                    selection = state.textContent.selection,
                )
            }.collectLatest { context ->
                val lists = completionProviders.mapNotNull { provider ->
                    try {
                        provider.complete(context)
                            ?.takeIf { it.items.isNotEmpty() }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        null
                    }
                }
                val primary = lists.firstOrNull()
                completionList = primary?.let { list ->
                    val mergedItems = lists
                        .filter { it.replacementRange == list.replacementRange }
                        .flatMap { it.items }
                        .distinctBy { it.label to it.insertText }
                        .sortedWith(
                            compareByDescending<ChatCompletionItem> { it.sortScore }
                                .thenBy { it.label.length }
                                .thenBy { it.label.lowercase() }
                        )
                        .take(8)
                    list.copy(items = mergedItems)
                }
            }
        }

        completionList?.takeIf { it.items.isNotEmpty() }?.let { list ->
            CompletionPopup(
                completionList = list,
                onItemClick = { item ->
                    state.applyCompletion(list.replacementRange, item)
                    completionList = null
                },
            )
        }

        val mentionMatch = remember(state.textContent.text) {
            Regex("""(?:^|\s)@([A-Za-z0-9_-]*)$""").find(state.textContent.text)
        }
        val skillQuery = mentionMatch?.groupValues?.getOrNull(1).orEmpty()
        val matchingSkills = remember(skillQuery, installedSkills) {
            installedSkills.filter { skill ->
                skillQuery.isBlank() ||
                    skill.name.contains(skillQuery, ignoreCase = true) ||
                    skill.description.contains(skillQuery, ignoreCase = true)
            }.take(8)
        }
        val matchingTools = remember(skillQuery, assistant.localTools) {
            assistant.localTools.filter { tool ->
                skillQuery.isBlank() || tool.mentionLabel().contains(skillQuery, ignoreCase = true)
            }.take(8)
        }
        if (mentionMatch != null && (matchingSkills.isNotEmpty() || matchingTools.isNotEmpty())) {
            SkillMentionPopup(
                skills = matchingSkills,
                tools = matchingTools,
                onSelect = { skill ->
                    val mentionStart = state.textContent.text.lastIndexOf('@').coerceAtLeast(0)
                    val replacement = "@${skill.name} "
                    state.textContent.edit {
                        replace(mentionStart, state.textContent.text.length, replacement)
                        selection = TextRange(mentionStart + replacement.length)
                    }
                    onUpdateAssistant(
                        assistant.copy(enabledSkills = assistant.enabledSkills + skill.name),
                    )
                },
                onToolSelect = { tool ->
                    val mentionStart = state.textContent.text.lastIndexOf('@').coerceAtLeast(0)
                    val replacement = "@${tool.mentionToken()} "
                    state.textContent.edit {
                        replace(mentionStart, state.textContent.text.length, replacement)
                        selection = TextRange(mentionStart + replacement.length)
                    }
                },
            )
        }

        TextField(
            state = state.textContent,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("chat_input")
                .contentReceiver(receiveContentListener)
                .onFocusChanged {
                    isFocused = it.isFocused
                },
            shape = MaterialTheme.shapes.largeIncreased,
            placeholder = {
                Text(stringResource(R.string.chat_input_placeholder))
            },
            lineLimits = TextFieldLineLimits.MultiLine(maxHeightInLines = 5),
            keyboardOptions = KeyboardOptions(
                imeAction = if (settings.displaySetting.sendOnEnter) ImeAction.Send else ImeAction.Default
            ),
            onKeyboardAction = {
                if (settings.displaySetting.sendOnEnter && !state.isEmpty()) {
                    onSendMessage()
                }
            },
            colors = TextFieldDefaults.colors().copy(
                unfocusedIndicatorColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
            ),
            leadingIcon = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    leadingAction()
                    if (quickMessages.isNotEmpty()) {
                        QuickMessageButton(quickMessages = quickMessages, state = state)
                    }
                }
            },
            trailingIcon = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isFocused) {
                        IconButton(onClick = { isFullScreen = !isFullScreen }) {
                            Icon(HugeIcons.FullScreen, null)
                        }
                    }
                    trailingAction()
                }
            },
        )
        if (isFullScreen) {
            FullScreenEditor(state = state) {
                isFullScreen = false
            }
        }
    }
}

@Composable
private fun CompletionPopup(
    completionList: ChatCompletionList,
    onItemClick: (ChatCompletionItem) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 280.dp),
        shape = RoundedCornerShape(18.dp),
        tonalElevation = 2.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        ) {
            items(
                items = completionList.items,
                key = { item -> "${item.label}:${item.insertText}" },
            ) { item ->
                Surface(
                    onClick = { onItemClick(item) },
                    modifier = Modifier.fillMaxWidth(),
                    color = Color.Transparent,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        item.icon?.let { icon ->
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                text = item.label,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            item.detail?.let { detail ->
                                Text(
                                    text = detail,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SkillMentionPopup(
    skills: List<SkillMetadata>,
    tools: List<LocalToolOption>,
    onSelect: (SkillMetadata) -> Unit,
    onToolSelect: (LocalToolOption) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 288.dp),
        shape = RoundedCornerShape(20.dp),
        color = glassSurface(
            GlassSurface.CHAT_INPUT,
            MaterialTheme.colorScheme.surfaceContainerHigh,
        ).container,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
        ) {
            Text(
                text = "Skills and tools",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
            skills.forEach { skill ->
                Surface(
                    onClick = { onSelect(skill) },
                    color = Color.Transparent,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Surface(
                            modifier = Modifier.size(32.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = HugeIcons.Zap,
                                    contentDescription = null,
                                    modifier = Modifier.size(17.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("@${skill.name}", style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = skill.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
            if (tools.isNotEmpty()) {
                Text(
                    text = "Agent tools",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                tools.forEach { tool ->
                    Surface(
                        onClick = { onToolSelect(tool) },
                        color = Color.Transparent,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Surface(
                                modifier = Modifier.size(32.dp),
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = HugeIcons.Zap,
                                        contentDescription = null,
                                        modifier = Modifier.size(17.dp),
                                        tint = MaterialTheme.colorScheme.secondary,
                                    )
                                }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text("@${tool.mentionLabel()}", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    text = "Enabled agent tool",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun LocalToolOption.mentionToken(): String = javaClass.simpleName

private fun LocalToolOption.mentionLabel(): String =
    mentionToken().replace(Regex("([a-z])([A-Z])"), "$1 $2")

private fun ChatInputState.applyCompletion(
    replacementRange: TextRange,
    item: ChatCompletionItem,
) {
    val textLength = textContent.text.length
    val start = replacementRange.min.coerceIn(0, textLength)
    val end = replacementRange.max.coerceIn(start, textLength)
    textContent.edit {
        replace(start, end, item.insertText)
        selection = TextRange(start + item.insertText.length)
    }
}

@Composable
private fun QuickMessageButton(
    quickMessages: List<QuickMessage>,
    state: ChatInputState,
) {
    var expanded by remember { mutableStateOf(false) }
    IconButton(
        onClick = {
            expanded = !expanded
        }) {
        Icon(HugeIcons.Zap, null)
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .widthIn(min = 200.dp, max = 360.dp)
        ) {
            quickMessages.forEach { quickMessage ->
                Surface(
                    onClick = {
                        state.appendText(quickMessage.content)
                        expanded = false
                    },
                    color = Color.Transparent,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(8.dp)
                    ) {
                        Text(
                            text = quickMessage.title,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = quickMessage.content,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FullScreenEditor(
    state: ChatInputState, onDone: () -> Unit
) {
    BasicAlertDialog(
        onDismissRequest = {
            onDone()
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false, decorFitsSystemWindows = false
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .imePadding(),
            verticalArrangement = Arrangement.Bottom
        ) {
            Surface(
                modifier = Modifier
                    .widthIn(max = 800.dp)
                    .fillMaxHeight(0.9f),
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(8.dp)
                        .fillMaxSize(),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row {
                        TextButton(
                            onClick = {
                                onDone()
                            }) {
                            Text(stringResource(R.string.chat_page_save))
                        }
                    }
                    TextField(
                        state = state.textContent,
                        modifier = Modifier
                            .padding(bottom = 2.dp)
                            .fillMaxSize(),
                        shape = RoundedCornerShape(32.dp),
                        placeholder = {
                            Text(stringResource(R.string.chat_input_placeholder))
                        },
                        colors = TextFieldDefaults.colors().copy(
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                        ),
                    )
                }
            }
        }
    }
}
