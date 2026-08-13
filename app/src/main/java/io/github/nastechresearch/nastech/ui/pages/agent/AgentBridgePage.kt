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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.nastechresearch.nastech.Screen
import io.github.nastechresearch.nastech.data.datastore.GlassSurface
import io.github.nastechresearch.nastech.ui.components.nav.BackButton
import io.github.nastechresearch.nastech.ui.context.LocalNavController
import io.github.nastechresearch.nastech.ui.theme.CustomColors
import io.github.nastechresearch.nastech.ui.theme.glassSurface
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

/**
 * Android-side control centre for the optional Nastech Agent companion.
 *
 * The bridge is deliberately opt-in. This screen explains the boundary and gives users direct
 * routes to the existing provider, skills, workspace, and approval surfaces before a host is
 * connected in a later protocol phase.
 */
@Composable
fun AgentBridgePage() {
    val navigator = LocalNavController.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var showSetupSheet by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("Nastech Agent") },
                navigationIcon = { BackButton() },
                actions = {
                    IconButton(onClick = { showSetupSheet = true }) {
                        Icon(HugeIcons.Add01, contentDescription = "Connect an agent")
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
                    onConnect = { showSetupSheet = true },
                    onOpenSkills = { navigator.navigate(Screen.Skills) },
                )
            }
            item {
                AgentStatusCard(onConnect = { showSetupSheet = true })
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
                    body = "Configure the model connection used by Nastech. A future Agent Bridge can use the same user-approved provider choices.",
                    icon = HugeIcons.Settings03,
                    onClick = { navigator.navigate(Screen.SettingProvider) },
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

    if (showSetupSheet) {
        AgentBridgeSetupSheet(
            onDismiss = { showSetupSheet = false },
            onOpenProviders = {
                showSetupSheet = false
                navigator.navigate(Screen.SettingProvider)
            },
        )
    }
}

@Composable
private fun AgentHero(onConnect: () -> Unit, onOpenSkills: () -> Unit) {
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
            Text("A supervised bridge to your growing agent", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(
                "Nastech Agent can provide optional sessions, skills, memory, and streamed task activity from a companion host. The Android app keeps setup, context, and approvals visible.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilledTonalButton(onClick = onConnect) { Text("Set up bridge") }
                OutlinedButton(onClick = onOpenSkills) { Text("Open skills") }
            }
        }
    }
}

@Composable
private fun AgentStatusCard(onConnect: () -> Unit) {
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
                Text("No Agent Bridge connected", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Your current chats and local features continue to work normally.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedButton(onClick = onConnect) { Text("Connect") }
        }
    }
}

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
private fun AgentBridgeSetupSheet(onDismiss: () -> Unit, onOpenProviders: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Connect Nastech Agent", style = MaterialTheme.typography.titleLarge)
            Text(
                "A bridge is optional. It lets this app communicate with an agent you run on a trusted device or service. Nastech will show the endpoint, connection status, shared context, and approvals before work begins.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SetupStep("1", "Prepare a trusted Agent host", "Run Nastech Agent on a device or service you control and enable its authenticated API server.")
            SetupStep("2", "Configure a provider", "Use Nastech’s existing provider settings to select your approved model connection.")
            SetupStep("3", "Add a bridge profile", "A forthcoming connection form will ask for the exact URL, profile, and authorization token. It will never take those details from ordinary chat messages.")
            SetupStep("4", "Review before allowing work", "Choose the skills, tools, workspace context, and approval rules for each bridge profile.")
            FilledTonalButton(onClick = onOpenProviders, modifier = Modifier.fillMaxWidth()) {
                Text("Open provider settings")
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SetupStep(number: String, title: String, body: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)) {
            Text(
                text = number,
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelLarge,
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
