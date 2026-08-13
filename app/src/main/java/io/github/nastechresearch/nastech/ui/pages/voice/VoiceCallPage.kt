package io.github.nastechresearch.nastech.ui.pages.voice

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nastechresearch.nastech.Screen
import io.github.nastechresearch.nastech.ui.components.ui.permission.PermissionManager
import io.github.nastechresearch.nastech.ui.components.ui.permission.PermissionRecordAudio
import io.github.nastechresearch.nastech.ui.components.ui.permission.rememberPermissionState
import io.github.nastechresearch.nastech.ui.context.LocalASRState
import io.github.nastechresearch.nastech.ui.context.LocalNavController
import io.github.nastechresearch.nastech.ui.context.LocalTTSState
import io.github.nastechresearch.nastech.ui.pages.chat.ChatVM
import io.github.nastechresearch.nastech.utils.removeBracketedContent
import io.github.nastechresearch.nastech.utils.stripMarkdown
import kotlinx.coroutines.flow.collect
import me.rerere.ai.core.MessageRole
import me.rerere.asr.ASRStatus
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowUp02
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.Mic01
import me.rerere.hugeicons.stroke.Settings03
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.uuid.Uuid

private enum class VoiceCallMode(val label: String, val detail: String) {
    UNAVAILABLE("Voice setup needed", "Choose a speech input provider before starting a call."),
    READY("Ready to talk", "Tap the microphone and speak naturally."),
    LISTENING("Listening", "Nastech is receiving your voice."),
    THINKING("Thinking", "Nastech is preparing a response."),
    SPEAKING("Speaking", "Tap the microphone to interrupt and respond."),
    ERROR("Voice needs attention", "Check the voice setup or try again."),
}

/**
 * A native Happy-inspired voice session surface. It reuses the active Nastech chat, ASR, TTS,
 * model, tools, approvals, and conversation history rather than creating a parallel voice stack.
 */
@Composable
fun VoiceCallPage(id: Uuid) {
    val vm: ChatVM = koinViewModel(parameters = { parametersOf(id.toString()) })
    val navigator = LocalNavController.current
    val asr = LocalASRState.current
    val tts = LocalTTSState.current
    val inputState = vm.inputState
    val permissionState = rememberPermissionState(PermissionRecordAudio)

    val settings by vm.settings.collectAsStateWithLifecycle()
    val conversation by vm.conversation.collectAsStateWithLifecycle()
    val generationJob by vm.conversationJob.collectAsStateWithLifecycle()
    val asrState by asr.state.collectAsState()
    val isTtsAvailable by tts.isAvailable.collectAsState()
    val isSpeaking by tts.isSpeaking.collectAsState()
    val currentConversation by rememberUpdatedState(conversation)

    PermissionManager(permissionState = permissionState)

    val mode = when {
        !asrState.isAvailable -> VoiceCallMode.UNAVAILABLE
        asrState.status == ASRStatus.Error -> VoiceCallMode.ERROR
        isSpeaking -> VoiceCallMode.SPEAKING
        generationJob != null -> VoiceCallMode.THINKING
        asrState.status == ASRStatus.Listening -> VoiceCallMode.LISTENING
        else -> VoiceCallMode.READY
    }

    LaunchedEffect(Unit) {
        vm.generationDoneFlow.collect { completedConversationId ->
            if (completedConversationId != id) return@collect
            val response = currentConversation.currentMessages.lastOrNull()
                ?.takeIf { it.role == MessageRole.ASSISTANT }
                ?.toText()
                ?.stripMarkdown()
                ?.removeBracketedContent()
                ?.trim()
            if (isTtsAvailable && !response.isNullOrBlank()) {
                tts.speak(response)
            }
        }
    }

    fun sendTranscript() {
        if (inputState.isEmpty() || generationJob != null) return
        vm.handleMessageSend(inputState.getContents())
        inputState.clearInput()
    }

    fun toggleListening() {
        when (asrState.status) {
            ASRStatus.Listening -> {
                asr.stop()
                sendTranscript()
            }
            ASRStatus.Idle, ASRStatus.Error -> {
                if (!permissionState.allRequiredPermissionsGranted) {
                    permissionState.requestPermissions()
                } else {
                    tts.stop()
                    asr.start { transcript -> inputState.setMessageText(transcript) }
                }
            }
            ASRStatus.Connecting, ASRStatus.Stopping -> Unit
        }
    }

    fun finishCall() {
        asr.stop()
        tts.stop()
        navigator.clearAndNavigate(Screen.Chat(id = id.toString()))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF05070B)),
    ) {
        VoiceCallOrb(
            mode = mode,
            modifier = Modifier.align(Alignment.Center),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 20.dp)
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            VoiceRoundButton(onClick = finishCall, icon = HugeIcons.Cancel01, description = "End voice call")
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Nastech Voice", style = MaterialTheme.typography.titleMedium, color = Color.White)
                Text(mode.label, style = MaterialTheme.typography.labelMedium, color = Color(0xFFBAC6FF))
            }
            VoiceRoundButton(
                onClick = { navigator.navigate(Screen.SettingSpeech) },
                icon = HugeIcons.Settings03,
                description = "Voice settings",
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                mode.detail,
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFDCE3FF),
            )
            TextField(
                state = inputState.textContent,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Type instead of speaking") },
                lineLimits = TextFieldLineLimits.SingleLine,
                trailingIcon = {
                    IconButton(onClick = { sendTranscript() }, enabled = !inputState.isEmpty() && generationJob == null) {
                        Icon(HugeIcons.ArrowUp02, contentDescription = "Send typed message")
                    }
                },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.White.copy(alpha = 0.10f),
                    unfocusedContainerColor = Color.White.copy(alpha = 0.08f),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedPlaceholderColor = Color(0xFFBEC8E8),
                    unfocusedPlaceholderColor = Color(0xFFBEC8E8),
                ),
                shape = MaterialTheme.shapes.large,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                FilledTonalButton(
                    onClick = { toggleListening() },
                    enabled = generationJob == null && mode != VoiceCallMode.UNAVAILABLE,
                    shape = CircleShape,
                    modifier = Modifier.size(72.dp),
                ) {
                    Icon(HugeIcons.Mic01, contentDescription = if (mode == VoiceCallMode.LISTENING) "Finish speaking" else "Start speaking")
                }
                FilledTonalButton(
                    onClick = finishCall,
                    shape = CircleShape,
                    modifier = Modifier.size(72.dp),
                ) {
                    Icon(HugeIcons.Cancel01, contentDescription = "End voice call")
                }
            }
        }
    }
}

@Composable
private fun VoiceCallOrb(mode: VoiceCallMode, modifier: Modifier = Modifier) {
    val targetScale = when (mode) {
        VoiceCallMode.LISTENING -> 1.12f
        VoiceCallMode.THINKING -> 1.04f
        VoiceCallMode.SPEAKING -> 1.16f
        else -> 1f
    }
    val scale by animateFloatAsState(targetValue = targetScale, animationSpec = tween(420, easing = FastOutSlowInEasing), label = "voice orb scale")
    val transition = rememberInfiniteTransition(label = "voice orb pulse")
    val pulse by transition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(if (mode == VoiceCallMode.SPEAKING) 620 else 1300, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "voice orb pulse amount",
    )
    val orbColors = when (mode) {
        VoiceCallMode.LISTENING -> listOf(Color(0xFF78E6FF), Color(0xFF3877FF), Color(0xFFB9C6FF))
        VoiceCallMode.THINKING -> listOf(Color(0xFFB6A5FF), Color(0xFF5A5DFF), Color(0xFFE3E8FF))
        VoiceCallMode.SPEAKING -> listOf(Color(0xFFFFC7E4), Color(0xFF8A73FF), Color(0xFFB3E5FF))
        VoiceCallMode.ERROR -> listOf(Color(0xFFFFB4AB), Color(0xFF9F2F33), Color(0xFFFFDAD6))
        else -> listOf(Color(0xFFC6D1FF), Color(0xFF7898FF), Color(0xFFE8ECFF))
    }
    Box(
        modifier = modifier
            .size(238.dp)
            .graphicsLayer(scaleX = scale * pulse, scaleY = scale * pulse)
            .clip(CircleShape)
            .background(Brush.radialGradient(orbColors)),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = CircleShape,
            color = Color.White.copy(alpha = 0.14f),
            modifier = Modifier.size(116.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = mode.label,
                    modifier = Modifier.padding(horizontal = 12.dp),
                    style = MaterialTheme.typography.titleSmall,
                    color = Color(0xFF0A1021),
                )
            }
        }
    }
}

@Composable
private fun VoiceRoundButton(
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = Color.White.copy(alpha = 0.10f),
        modifier = Modifier.size(48.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = description, tint = Color.White)
        }
    }
}
