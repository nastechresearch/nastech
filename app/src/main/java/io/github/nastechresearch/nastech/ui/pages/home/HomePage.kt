package io.github.nastechresearch.nastech.ui.pages.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nastechresearch.nastech.Screen
import io.github.nastechresearch.nastech.data.datastore.GlassSurface
import io.github.nastechresearch.nastech.ui.context.LocalNavController
import io.github.nastechresearch.nastech.ui.pages.chat.MeshGradientBackground
import io.github.nastechresearch.nastech.ui.pages.setting.SettingVM
import io.github.nastechresearch.nastech.ui.theme.CustomColors
import io.github.nastechresearch.nastech.ui.theme.glassSurface
import io.github.nastechresearch.nastech.utils.navigateToChatPage
import io.github.nastechresearch.nastech.utils.plus
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Bookshelf01
import me.rerere.hugeicons.stroke.Folder01
import me.rerere.hugeicons.stroke.GlobalSearch
import me.rerere.hugeicons.stroke.Image02
import me.rerere.hugeicons.stroke.Message01
import me.rerere.hugeicons.stroke.MessageAdd01
import me.rerere.hugeicons.stroke.Mic01
import me.rerere.hugeicons.stroke.Sparkles
import org.koin.androidx.compose.koinViewModel

private enum class WorkspaceTab {
    HOME,
    DISCOVER,
    LIBRARY,
}

@Composable
fun HomePage(vm: SettingVM = koinViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val navigator = LocalNavController.current
    var selectedTab by remember { mutableStateOf(WorkspaceTab.HOME) }
    var showQuickActions by remember { mutableStateOf(false) }
    var entered by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { entered = true }

    MeshGradientBackground {
        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = {
                NastechWorkspaceNavigation(
                    selectedTab = selectedTab,
                    onTabSelected = { selectedTab = it },
                    onNewConversation = { navigateToChatPage(navigator) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                )
            },
        ) { contentPadding ->
            AnimatedVisibility(
                visible = entered,
                enter = fadeIn(animationSpec = spring(stiffness = 240f)) +
                    scaleIn(initialScale = 0.94f, animationSpec = spring(stiffness = 260f)),
                modifier = Modifier.fillMaxSize(),
            ) {
                when (selectedTab) {
                    WorkspaceTab.HOME -> HomeFeed(
                        userName = settings.displaySetting.userNickname.ifBlank { "there" },
                        contentPadding = contentPadding,
                        onQuickActions = { showQuickActions = true },
                        onNewConversation = { navigateToChatPage(navigator) },
                        onVoice = { navigator.navigate(Screen.SettingSpeech) },
                        onImage = { navigator.navigate(Screen.ImageGen) },
                        onAgent = { navigator.navigate(Screen.AgentBridge) },
                    )

                    WorkspaceTab.DISCOVER -> DiscoverFeed(
                        contentPadding = contentPadding,
                        onStartPrompt = { prompt -> navigateToChatPage(navigator, initText = prompt) },
                    )

                    WorkspaceTab.LIBRARY -> LibraryFeed(
                        contentPadding = contentPadding,
                        onHistory = { navigator.navigate(Screen.History) },
                        onSaved = { navigator.navigate(Screen.Favorite) },
                        onWorkspaces = { navigator.navigate(Screen.Workspaces) },
                        onSkills = { navigator.navigate(Screen.Skills) },
                    )
                }
            }
        }

        if (showQuickActions) {
            WorkspaceQuickActionsSheet(
                onDismiss = { showQuickActions = false },
                onNewConversation = {
                    showQuickActions = false
                    navigateToChatPage(navigator)
                },
                onImage = {
                    showQuickActions = false
                    navigator.navigate(Screen.ImageGen)
                },
                onVoice = {
                    showQuickActions = false
                    navigator.navigate(Screen.SettingSpeech)
                },
                onWorkspace = {
                    showQuickActions = false
                    navigator.navigate(Screen.Workspaces)
                },
            )
        }
    }
}

@Composable
private fun HomeFeed(
    userName: String,
    contentPadding: PaddingValues,
    onQuickActions: () -> Unit,
    onNewConversation: () -> Unit,
    onVoice: () -> Unit,
    onImage: () -> Unit,
    onAgent: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding + PaddingValues(horizontal = 20.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Nastech", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text(
                    text = "Welcome back, $userName",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "A focused workspace for conversations, research, and the tools you choose to connect.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onNewConversation),
                colors = CustomColors.cardColors,
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                        modifier = Modifier.size(52.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(HugeIcons.MessageAdd01, contentDescription = null)
                        }
                    }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Start a focused conversation", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Ask, plan, write, analyze, or use an approved tool.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        item {
            Text("Quick actions", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                item { HomeActionCard("New chat", HugeIcons.MessageAdd01, onNewConversation) }
                item { HomeActionCard("Voice", HugeIcons.Mic01, onVoice) }
                item { HomeActionCard("Create image", HugeIcons.Image02, onImage) }
                item { HomeActionCard("Agent", HugeIcons.Sparkles, onAgent) }
                item { HomeActionCard("More", HugeIcons.Sparkles, onQuickActions) }
            }
        }
        item {
            WorkspaceInsightCard(
                title = "Your workspace, your controls",
                body = "Fine-tune glass materials, colors, text size, providers, permissions, and voice in Settings. Nastech keeps those choices visible and in your control.",
                icon = HugeIcons.Sparkles,
            )
        }
    }
}

@Composable
private fun DiscoverFeed(contentPadding: PaddingValues, onStartPrompt: (String) -> Unit) {
    val prompts = listOf(
        DiscoverPrompt("Plan a project", "Turn an idea into milestones, risks, and next actions.", "Help me plan a project step by step."),
        DiscoverPrompt("Research a topic", "Ask for a structured brief with sources to review.", "Research this topic and create a concise brief: "),
        DiscoverPrompt("Draft something", "Start a polished email, proposal, note, or outline.", "Help me draft: "),
    )
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding + PaddingValues(horizontal = 20.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Discover", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "Intentional starting points for your next conversation.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(prompts) { prompt ->
            Card(
                modifier = Modifier.fillMaxWidth().clickable { onStartPrompt(prompt.message) },
                colors = CustomColors.cardColors,
            ) {
                Row(
                    modifier = Modifier.padding(18.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier.size(44.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) { Icon(HugeIcons.Sparkles, null) }
                    }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(prompt.title, style = MaterialTheme.typography.titleMedium)
                        Text(prompt.body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryFeed(
    contentPadding: PaddingValues,
    onHistory: () -> Unit,
    onSaved: () -> Unit,
    onWorkspaces: () -> Unit,
    onSkills: () -> Unit,
) {
    val items = listOf(
        LibraryDestination("Conversation history", "Return to previous conversations and continue where you left off.", HugeIcons.Message01, onHistory),
        LibraryDestination("Saved messages", "Keep the answers and notes that matter to you.", HugeIcons.Bookshelf01, onSaved),
        LibraryDestination("Workspaces", "Organize local workspace files used by your assistants.", HugeIcons.Folder01, onWorkspaces),
        LibraryDestination("Skills", "Browse and manage reusable assistant capabilities.", HugeIcons.Sparkles, onSkills),
    )
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding + PaddingValues(horizontal = 20.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Library", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "The conversations, files, and reusable building blocks in your Nastech workspace.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(items) { item ->
            Card(
                modifier = Modifier.fillMaxWidth().clickable(onClick = item.onClick),
                colors = CustomColors.cardColors,
            ) {
                Row(
                    modifier = Modifier.padding(18.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(item.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(item.title, style = MaterialTheme.typography.titleMedium)
                        Text(item.body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun NastechWorkspaceNavigation(
    selectedTab: WorkspaceTab,
    onTabSelected: (WorkspaceTab) -> Unit,
    onNewConversation: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val glass = glassSurface(GlassSurface.BOTTOM_BAR, MaterialTheme.colorScheme.surfaceContainer)
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        color = glass.container,
        border = androidx.compose.foundation.BorderStroke(1.dp, glass.border),
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WorkspaceNavItem("Home", HugeIcons.Message01, selectedTab == WorkspaceTab.HOME) { onTabSelected(WorkspaceTab.HOME) }
            WorkspaceNavItem("Discover", HugeIcons.GlobalSearch, selectedTab == WorkspaceTab.DISCOVER) { onTabSelected(WorkspaceTab.DISCOVER) }
            WorkspaceNavItem("Library", HugeIcons.Bookshelf01, selectedTab == WorkspaceTab.LIBRARY) { onTabSelected(WorkspaceTab.LIBRARY) }
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                modifier = Modifier.size(48.dp).clickable(onClick = onNewConversation),
            ) {
                Box(contentAlignment = Alignment.Center) { Icon(HugeIcons.MessageAdd01, contentDescription = "New conversation") }
            }
        }
    }
}

@Composable
private fun RowScope.WorkspaceNavItem(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .weight(1f)
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun HomeActionCard(label: String, icon: ImageVector, onClick: () -> Unit) {
    Card(
        modifier = Modifier.width(132.dp).clickable(onClick = onClick),
        colors = CustomColors.cardColors,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.titleSmall)
        }
    }
}

@Composable
private fun WorkspaceInsightCard(title: String, body: String, icon: ImageVector) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CustomColors.cardColors) {
        Row(
            modifier = Modifier.padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun WorkspaceQuickActionsSheet(
    onDismiss: () -> Unit,
    onNewConversation: () -> Unit,
    onImage: () -> Unit,
    onVoice: () -> Unit,
    onWorkspace: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Create in Nastech", style = MaterialTheme.typography.titleLarge)
            Text(
                "Start with a focused action, then continue in the workspace you already know.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            QuickSheetAction("New conversation", "Start with a blank chat", HugeIcons.MessageAdd01, onNewConversation)
            QuickSheetAction("Create an image", "Open image generation", HugeIcons.Image02, onImage)
            QuickSheetAction("Voice settings", "Choose speech and voice input providers", HugeIcons.Mic01, onVoice)
            QuickSheetAction("Open workspaces", "Manage assistant files and local projects", HugeIcons.Folder01, onWorkspace)
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun QuickSheetAction(label: String, description: String, icon: ImageVector, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CustomColors.cardColors,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(label, style = MaterialTheme.typography.titleMedium)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private data class DiscoverPrompt(val title: String, val body: String, val message: String)

private data class LibraryDestination(
    val title: String,
    val body: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
)
