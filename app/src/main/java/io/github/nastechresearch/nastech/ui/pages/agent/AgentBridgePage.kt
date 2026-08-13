package io.github.nastechresearch.nastech.ui.pages.agent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nastechresearch.nastech.Screen
import io.github.nastechresearch.nastech.data.datastore.GlassSurface
import io.github.nastechresearch.nastech.data.preferences.ToolApprovalPreferences
import io.github.nastechresearch.nastech.data.repository.ConversationRepository
import io.github.nastechresearch.nastech.ui.components.nav.BackButton
import io.github.nastechresearch.nastech.ui.context.LocalNavController
import io.github.nastechresearch.nastech.ui.theme.CustomColors
import io.github.nastechresearch.nastech.ui.theme.glassSurface
import io.github.nastechresearch.nastech.ui.pages.setting.SettingVM
import io.github.nastechresearch.nastech.utils.navigateToChatPage
import io.github.nastechresearch.nastech.utils.plus
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.Bookshelf01
import me.rerere.hugeicons.stroke.Folder01
import me.rerere.hugeicons.stroke.Message01
import me.rerere.hugeicons.stroke.Puzzle
import me.rerere.hugeicons.stroke.Settings03
import me.rerere.hugeicons.stroke.Shield01
import me.rerere.hugeicons.stroke.Sparkles
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

/**
 * One connected Nastech Agent control centre. Actions here either open an existing Nastech
 * capability or create a regular Nastech conversation with a focused, editable starting prompt.
 */
@Composable
fun AgentBridgePage(vm: SettingVM = koinViewModel()) {
    val navigator = LocalNavController.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val approvalPreferences: ToolApprovalPreferences = koinInject()
    val alwaysAllowedTools by approvalPreferences.alwaysAllowFlow.collectAsStateWithLifecycle(initialValue = emptySet())
    val conversationRepository: ConversationRepository = koinInject()
    val activeConversations by conversationRepository
        .getConversationsOfAssistant(settings.assistantId)
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val configuredModelCount = settings.providers.sumOf { it.models.size }
    val enabledSkillCount = settings.assistants.sumOf { it.enabledSkills.size }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var showActionsSheet by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("Nastech Agent") },
                navigationIcon = { BackButton() },
                actions = {
                    IconButton(onClick = { showActionsSheet = true }) {
                        Icon(HugeIcons.Add01, contentDescription = "Start an agent task")
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = innerPadding + PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                AgentHero(
                    onStartTask = { navigateToChatPage(navigator, initText = "Help me plan and complete this task: ") },
                    onOpenSkills = { navigator.navigate(Screen.Skills) },
                )
            }
            item {
                AgentStatusCard(onStartTask = { navigateToChatPage(navigator) })
            }
            item {
                AgentInsightsCard(
                    conversationCount = activeConversations.size,
                    configuredModelCount = configuredModelCount,
                    enabledSkillCount = enabledSkillCount,
                    autoCompactionEnabled = settings.enableAutoCompaction,
                    onOpenHistory = { navigator.navigate(Screen.History) },
                    onOpenCompaction = { navigator.navigate(Screen.SettingModels) },
                )
            }
            item {
                AgentMilestonesCard(
                    conversationCount = activeConversations.size,
                    configuredModelCount = configuredModelCount,
                    enabledSkillCount = enabledSkillCount,
                    grantedToolCount = alwaysAllowedTools.size,
                    voiceInputReady = settings.selectedASRProviderId != null,
                    onOpenHistory = { navigator.navigate(Screen.History) },
                    onOpenProviders = { navigator.navigate(Screen.SettingProvider) },
                    onOpenSkills = { navigator.navigate(Screen.Skills) },
                    onOpenApprovals = { navigator.navigate(Screen.SettingToolApprovals) },
                    onOpenVoice = { navigator.navigate(Screen.SettingSpeech) },
                )
            }
            item {
                Text("What stays in your control", style = MaterialTheme.typography.titleMedium)
            }
            item {
                AgentControlCard(
                    title = "Skills and work modes",
                    body = "Review a skill’s source, requirements, and scope before enabling it. Use the local Skills Library for on-device guidance and imports.",
                    icon = HugeIcons.Puzzle,
                    onClick = { navigator.navigate(Screen.Skills) },
                )
            }
            item {
                AgentControlCard(
                    title = "Workspace context",
                    body = "Choose the files and workspaces that belong to a task. Context is attached deliberately, not collected silently.",
                    icon = HugeIcons.Folder01,
                    onClick = { navigator.navigate(Screen.Workspaces) },
                )
            }
            item {
                AgentControlCard(
                    title = "Tool approvals",
                    body = "See agent actions in the activity stream and retain the final decision for external or mutating work.",
                    icon = HugeIcons.Shield01,
                    onClick = { navigator.navigate(Screen.SettingToolApprovals) },
                )
            }
            item {
                AgentControlCard(
                    title = "Provider and model routing",
                    body = "Choose the model connection used by the conversations, skills, and tools already in Nastech.",
                    icon = HugeIcons.Settings03,
                    onClick = { navigator.navigate(Screen.SettingProvider) },
                )
            }
            item {
                AgentControlCard(
                    title = "Voice in chat",
                    body = "Configure speech input and spoken replies used directly from the Nastech chat composer.",
                    icon = HugeIcons.Message01,
                    onClick = { navigator.navigate(Screen.SettingSpeech) },
                )
            }
            item {
                AgentControlCard(
                    title = "Sub-agents",
                    body = "Create focused assistant roles for larger tasks from the same Nastech conversation workflow.",
                    icon = HugeIcons.Sparkles,
                    onClick = { navigator.navigate(Screen.SettingSubAgents) },
                )
            }
            item {
                AgentControlCard(
                    title = "Conversation library",
                    body = "Keep previous discussions, saved messages, and workspace material organized inside Nastech.",
                    icon = HugeIcons.Bookshelf01,
                    onClick = { navigator.navigate(Screen.History) },
                )
            }
            item {
                Spacer(Modifier.height(12.dp))
            }
        }
    }

    if (showActionsSheet) {
        AgentActionsSheet(
            onDismiss = { showActionsSheet = false },
            onStartPlanning = {
                showActionsSheet = false
                navigateToChatPage(navigator, initText = "Help me plan this goal step by step: ")
            },
            onStartResearch = {
                showActionsSheet = false
                navigateToChatPage(navigator, initText = "Research this topic and prepare a structured brief: ")
            },
            onStartVoice = {
                showActionsSheet = false
                navigateToChatPage(navigator, initText = "Start a voice-enabled conversation: ")
            },
            onOpenSkills = {
                showActionsSheet = false
                navigator.navigate(Screen.Skills)
            },
            onOpenWorkspaces = {
                showActionsSheet = false
                navigator.navigate(Screen.Workspaces)
            },
        )
    }
}

@Composable
private fun AgentHero(onStartTask: () -> Unit, onOpenSkills: () -> Unit) {
    val glass = glassSurface(GlassSurface.CARD, MaterialTheme.colorScheme.surfaceContainer)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = glass.container),
        border = androidx.compose.foundation.BorderStroke(1.dp, glass.border),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                modifier = Modifier.size(52.dp),
            ) {
                androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                    Icon(HugeIcons.Sparkles, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
            }
            Text("One workspace for every agent task", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(
                "Start an ordinary Nastech conversation, then add the skills, workspace context, model, voice, and approvals that belong to the task. Everything stays in the same app flow.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilledTonalButton(onClick = onStartTask) { Text("Start a task") }
                OutlinedButton(onClick = onOpenSkills) { Text("Open skills") }
            }
        }
    }
}

@Composable
private fun AgentStatusCard(onStartTask: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CustomColors.cardColors,
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(44.dp),
            ) {
                androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                    Icon(HugeIcons.Message01, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("Agent actions stay in your chats", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Use a focused starter, then continue with the same chat history, skills, files, provider, and approval controls.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedButton(onClick = onStartTask) { Text("Start") }
        }
    }
}

@Composable
private fun AgentInsightsCard(
    conversationCount: Int,
    configuredModelCount: Int,
    enabledSkillCount: Int,
    autoCompactionEnabled: Boolean,
    onOpenHistory: () -> Unit,
    onOpenCompaction: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CustomColors.cardColors) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Agent insights", style = MaterialTheme.typography.titleMedium)
            Text(
                "$conversationCount conversation${if (conversationCount == 1) "" else "s"} with the current assistant · $configuredModelCount model${if (configuredModelCount == 1) "" else "s"} available · $enabledSkillCount enabled skill${if (enabledSkillCount == 1) "" else "s"}.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                if (autoCompactionEnabled) "Context compaction is enabled for longer chats."
                else "Context compaction is off; you can enable it when longer chats need more room.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onOpenHistory) { Text("Open history") }
                OutlinedButton(onClick = onOpenCompaction) { Text("Context settings") }
            }
        }
    }
}

@Composable
private fun AgentMilestonesCard(
    conversationCount: Int,
    configuredModelCount: Int,
    enabledSkillCount: Int,
    grantedToolCount: Int,
    voiceInputReady: Boolean,
    onOpenHistory: () -> Unit,
    onOpenProviders: () -> Unit,
    onOpenSkills: () -> Unit,
    onOpenApprovals: () -> Unit,
    onOpenVoice: () -> Unit,
) {
    val milestones = listOf(
        AgentMilestone("First conversation", "A saved chat unlocks this local milestone.", conversationCount > 0, onOpenHistory),
        AgentMilestone("Model catalogue ready", "Add or configure a provider model to prepare this milestone.", configuredModelCount > 0, onOpenProviders),
        AgentMilestone("Skill toolkit", "Enable a skill for an assistant to prepare this milestone.", enabledSkillCount > 0, onOpenSkills),
        AgentMilestone("Trusted tool decision", "An Always Allow approval is recorded here when you choose one.", grantedToolCount > 0, onOpenApprovals),
        AgentMilestone("Voice input ready", "Choose an ASR provider to prepare voice input.", voiceInputReady, onOpenVoice),
    )
    Card(modifier = Modifier.fillMaxWidth(), colors = CustomColors.cardColors) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Milestones", style = MaterialTheme.typography.titleMedium)
            Text(
                "These indicators read your local Nastech setup and chat state. They do not send analytics or create a separate agent profile.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            milestones.forEach { milestone ->
                Surface(
                    onClick = milestone.onClick,
                    shape = MaterialTheme.shapes.medium,
                    color = if (milestone.complete) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = if (milestone.complete) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = if (milestone.complete) "✓" else "○",
                            style = MaterialTheme.typography.titleMedium,
                            color = if (milestone.complete) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                        )
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(milestone.title, style = MaterialTheme.typography.titleSmall)
                            Text(
                                if (milestone.complete) "Ready in your current Nastech setup." else milestone.nextStep,
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

private data class AgentMilestone(
    val title: String,
    val nextStep: String,
    val complete: Boolean,
    val onClick: () -> Unit,
)

@Composable
private fun AgentControlCard(
    title: String,
    body: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CustomColors.cardColors,
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun AgentActionsSheet(
    onDismiss: () -> Unit,
    onStartPlanning: () -> Unit,
    onStartResearch: () -> Unit,
    onStartVoice: () -> Unit,
    onOpenSkills: () -> Unit,
    onOpenWorkspaces: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Start an agent task", style = MaterialTheme.typography.titleLarge)
            Text(
                "Choose a focused starting point. Each option opens the same Nastech conversation experience, where you can change the prompt, model, skills, voice, and tool permissions before anything acts.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            AgentSheetAction("Plan a project", "Open a chat with a project-planning starter", HugeIcons.Sparkles, onStartPlanning)
            AgentSheetAction("Research a topic", "Open a chat with a research-brief starter", HugeIcons.Message01, onStartResearch)
            AgentSheetAction("Start a voice-enabled chat", "Open the normal composer with speech input and the docked reader", HugeIcons.Message01, onStartVoice)
            AgentSheetAction("Choose skills", "Manage reusable capabilities for your assistants", HugeIcons.Puzzle, onOpenSkills)
            AgentSheetAction("Attach workspace context", "Open local workspaces and project files", HugeIcons.Folder01, onOpenWorkspaces)
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun AgentSheetAction(label: String, description: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CustomColors.cardColors,
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(label, style = MaterialTheme.typography.titleMedium)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
