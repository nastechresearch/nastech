package io.github.nastechresearch.nastech.ui.pages.welcome

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nastechresearch.nastech.BuildConfig
import io.github.nastechresearch.nastech.Screen
import io.github.nastechresearch.nastech.ui.context.LocalNavController
import io.github.nastechresearch.nastech.ui.pages.chat.MeshGradientBackground
import io.github.nastechresearch.nastech.ui.pages.setting.SettingVM
import io.github.nastechresearch.nastech.ui.theme.CustomColors
import io.github.nastechresearch.nastech.utils.plus
import org.koin.androidx.compose.koinViewModel

@Composable
fun WelcomePage(chatId: String, vm: SettingVM = koinViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val navigator = LocalNavController.current
    var page by remember { mutableIntStateOf(0) }
    var acceptedTerms by remember { mutableStateOf(false) }

    MeshGradientBackground {
        Scaffold(
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
        ) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = padding + PaddingValues(horizontal = 20.dp, vertical = 28.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                item {
                    Text("Nastech", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                    Text(
                        text = when (page) {
                            0 -> "A private, configurable assistant for conversations and approved actions."
                            1 -> "Start with the capabilities you want, and keep every step under your control."
                            else -> "Review the terms before starting your first Nastech conversation."
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                item {
                    when (page) {
                        0 -> WelcomeOverview()
                        1 -> WelcomeCapabilities()
                        else -> WelcomeTerms(
                            accepted = acceptedTerms,
                            onAcceptedChange = { acceptedTerms = it },
                        )
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (page > 0) {
                            OutlinedButton(
                                onClick = { page -= 1 },
                                modifier = Modifier.weight(1f),
                            ) { Text("Back") }
                        }
                        Button(
                            onClick = {
                                if (page < 2) {
                                    page += 1
                                } else {
                                    vm.updateSettings(
                                        settings.copy(onboardingAcceptedVersion = BuildConfig.VERSION_NAME),
                                    )
                                    navigator.clearAndNavigate(Screen.Chat(chatId))
                                }
                            },
                            enabled = page < 2 || acceptedTerms,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(if (page < 2) "Continue" else "Accept and start")
                        }
                    }
                }

                item {
                    Text(
                        text = "Step ${page + 1} of 3 · Version ${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun WelcomeOverview() {
    WelcomeCard(
        title = "Your Nastech workspace",
        body = "Choose your AI provider, customize the interface, and decide which optional tools are available. Nastech keeps conversations, settings, and approvals in one place.",
    )
    WelcomeCard(
        title = "Built for control",
        body = "Actions that need approval remain visible in the chat. You can inspect provider settings, permissions, and diagnostics before enabling advanced capabilities.",
    )
}

@Composable
private fun WelcomeCapabilities() {
    WelcomeCard(
        title = "Chat and guided choices",
        body = "Ask for a plan, then choose a suggested next step with one tap when the agent offers options. You can always write your own response instead.",
    )
    WelcomeCard(
        title = "Tools, workspace, and browser",
        body = "Enable only the capabilities you need, including files, optional local tools, search, and browser-assisted tasks. Nastech shows relevant activity in the conversation.",
    )
    WelcomeCard(
        title = "Voice and accessibility",
        body = "Nastech supports configurable speech and voice input providers. Voice capabilities remain optional and require their own provider and permission setup.",
    )
}

@Composable
private fun WelcomeTerms(accepted: Boolean, onAcceptedChange: (Boolean) -> Unit) {
    WelcomeCard(
        title = "Terms, privacy, and notices",
        body = "Nastech can connect to services and optional tools that you configure. Review provider terms, protect your credentials, and approve sensitive actions only when you understand their effect. The application licence and source notices remain available in Settings and the Nastech Research documentation site.",
    )
    Card(colors = CustomColors.cardColors, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Switch(checked = accepted, onCheckedChange = onAcceptedChange)
            Text(
                "I have reviewed and accept the Nastech Terms, Privacy information, and licence notices.",
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun WelcomeCard(title: String, body: String) {
    Card(
        colors = CustomColors.cardColors,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
