package io.github.nastechresearch.nastech.ui.pages.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.nastechresearch.nastech.Screen
import io.github.nastechresearch.nastech.data.datastore.GlassSurface
import io.github.nastechresearch.nastech.ui.context.LocalNavController
import io.github.nastechresearch.nastech.ui.components.ui.ReleaseUpdateNotice
import io.github.nastechresearch.nastech.ui.theme.glassContentColor
import io.github.nastechresearch.nastech.ui.theme.glassSurface
import io.github.nastechresearch.nastech.utils.navigateToChatPage
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowUp01
import me.rerere.hugeicons.stroke.FileAdd
import me.rerere.hugeicons.stroke.Menu03
import me.rerere.hugeicons.stroke.Settings03
import me.rerere.hugeicons.stroke.Voice

/**
 * Chat-first Nastech entry surface. It replaces the former Terms gate with an immediately useful
 * task workspace while preserving the existing conversation, agent, file, and speech flows.
 */
@Composable
fun AgentWorkspaceHomePage() {
    val navigator = LocalNavController.current
    val background = glassSurface(GlassSurface.APP_BACKGROUND, Color.Black)
    val contentColor = glassContentColor(GlassSurface.APP_BACKGROUND, Color(0xFFF5F9FF))
    val composerGlass = glassSurface(GlassSurface.CHAT_INPUT, Color(0xFF0C1017))
    val composerContent = glassContentColor(GlassSurface.CHAT_INPUT, Color(0xFFF5F9FF))
    var task by rememberSaveable { mutableStateOf("") }
    var menuExpanded by remember { mutableStateOf(false) }

    fun startTask(text: String = task) {
        navigateToChatPage(
            navigator = navigator,
            initText = text.trim().ifBlank { "Help me decide what to put to work today." },
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(background.container),
    ) {
        QuietDotField(modifier = Modifier.fillMaxSize())
        ReleaseUpdateNotice()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp),
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = composerGlass.container.copy(alpha = 0.88f),
                    contentColor = composerContent,
                    modifier = Modifier
                        .padding(top = 12.dp)
                        .clip(RoundedCornerShape(16.dp)),
                ) {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(
                            imageVector = HugeIcons.Menu03,
                            contentDescription = "Open workspace menu",
                        )
                    }
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    modifier = Modifier.padding(top = 64.dp),
                ) {
                    DropdownMenuItem(
                        text = { Text("Agent hub") },
                        onClick = {
                            menuExpanded = false
                            navigator.navigate(Screen.AgentBridge)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Conversation history") },
                        onClick = {
                            menuExpanded = false
                            navigator.navigate(Screen.History)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Settings") },
                        leadingIcon = {
                            Icon(HugeIcons.Settings03, contentDescription = null)
                        },
                        onClick = {
                            menuExpanded = false
                            navigator.navigate(Screen.Setting)
                        },
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1.35f))

            Column(
                verticalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                NastechMark(color = contentColor)
                Text(
                    text = "What will you put to work?",
                    style = TextStyle(
                        color = contentColor,
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Normal,
                        fontSize = 45.sp,
                        lineHeight = 52.sp,
                        letterSpacing = (-1.1).sp,
                    ),
                )

                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = composerGlass.container,
                    contentColor = composerContent,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Box(modifier = Modifier.fillMaxWidth()) {
                            if (task.isBlank()) {
                                Text(
                                    text = "Describe a task, question, or goal…",
                                    color = composerContent.copy(alpha = 0.56f),
                                    style = MaterialTheme.typography.titleMedium,
                                )
                            }
                            BasicTextField(
                                value = task,
                                onValueChange = { task = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(104.dp),
                                textStyle = MaterialTheme.typography.titleMedium.copy(color = composerContent),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                keyboardActions = KeyboardActions(onSend = { startTask() }),
                                maxLines = 4,
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            WorkspaceIconButton(
                                icon = HugeIcons.FileAdd,
                                description = "Open files",
                                onClick = { navigator.navigate(Screen.SettingFiles) },
                            )
                            WorkspacePill(
                                label = "Nastech Agent",
                                onClick = { navigator.navigate(Screen.AgentBridge) },
                                modifier = Modifier.weight(1f),
                            )
                            WorkspaceIconButton(
                                icon = HugeIcons.Voice,
                                description = "Open voice settings",
                                onClick = { navigator.navigate(Screen.SettingSpeech) },
                            )
                            WorkspaceIconButton(
                                icon = HugeIcons.ArrowUp01,
                                description = "Start task",
                                highlighted = task.isNotBlank(),
                                onClick = { startTask() },
                            )
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        WorkspacePill(
                            label = "Automate",
                            onClick = { task = "Automate this workflow: " },
                            modifier = Modifier.weight(1f),
                        )
                        WorkspacePill(
                            label = "Agent hub",
                            onClick = { navigator.navigate(Screen.AgentBridge) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        WorkspacePill(
                            label = "Research & extract",
                            onClick = { task = "Research and extract the key findings from: " },
                            modifier = Modifier.weight(1.25f),
                        )
                        WorkspacePill(
                            label = "Voice",
                            onClick = { task = "Start a voice-enabled conversation about: " },
                            modifier = Modifier.weight(0.75f),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun QuietDotField(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val spacing = 18.dp.toPx()
        val radius = 0.8.dp.toPx()
        var x = spacing / 2f
        while (x < size.width) {
            var y = spacing / 2f
            while (y < size.height) {
                drawCircle(
                    color = Color(0xFF96A7C4).copy(alpha = 0.10f),
                    radius = radius,
                    center = androidx.compose.ui.geometry.Offset(x, y),
                )
                y += spacing
            }
            x += spacing
        }
    }
}

@Composable
private fun NastechMark(color: Color) {
    Text(
        text = "✦  ◌  ▤  ●  ▦",
        color = color,
        style = MaterialTheme.typography.headlineMedium.copy(
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 3.sp,
        ),
    )
}

@Composable
private fun WorkspacePill(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val glass = glassSurface(GlassSurface.BUTTON, MaterialTheme.colorScheme.surfaceVariant)
    val content = glassContentColor(GlassSurface.BUTTON, MaterialTheme.colorScheme.onSurface)
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .clickable(onClick = onClick),
        color = glass.container,
        contentColor = content,
        shape = RoundedCornerShape(999.dp),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 11.dp),
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun WorkspaceIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
    highlighted: Boolean = false,
) {
    val glass = glassSurface(GlassSurface.BUTTON, MaterialTheme.colorScheme.surfaceVariant)
    val content = glassContentColor(GlassSurface.BUTTON, MaterialTheme.colorScheme.onSurface)
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (highlighted) MaterialTheme.colorScheme.primary else glass.container,
        contentColor = if (highlighted) MaterialTheme.colorScheme.onPrimary else content,
    ) {
        IconButton(onClick = onClick) {
            Icon(icon, contentDescription = description)
        }
    }
}
