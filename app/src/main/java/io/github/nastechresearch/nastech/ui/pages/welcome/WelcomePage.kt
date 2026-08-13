package io.github.nastechresearch.nastech.ui.pages.welcome

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nastechresearch.nastech.BuildConfig
import io.github.nastechresearch.nastech.R
import io.github.nastechresearch.nastech.Screen
import io.github.nastechresearch.nastech.ui.context.LocalNavController
import io.github.nastechresearch.nastech.ui.pages.chat.MeshGradientBackground
import io.github.nastechresearch.nastech.ui.pages.setting.SettingVM
import io.github.nastechresearch.nastech.ui.theme.CustomColors
import io.github.nastechresearch.nastech.utils.plus
import org.koin.androidx.compose.koinViewModel

/**
 * The mandatory first-launch journey remains chat-first. Its visual pages introduce configurable
 * capabilities, then Terms acceptance opens the same normal conversation route used elsewhere.
 */
@Composable
fun WelcomePage(chatId: String, vm: SettingVM = koinViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val navigator = LocalNavController.current
    var page by remember { mutableIntStateOf(0) }
    var acceptedTerms by remember { mutableStateOf(false) }
    var rating by remember(settings.onboardingRating) { mutableIntStateOf(settings.onboardingRating) }

    MeshGradientBackground {
        Scaffold(containerColor = androidx.compose.ui.graphics.Color.Transparent) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = padding + PaddingValues(horizontal = 20.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    Text(
                        text = "Nastech",
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = when (page) {
                            0 -> "Start with a private, configurable assistant that stays under your control."
                            1 -> "Connect only the voice, provider, tools, and optional services that fit your workflow."
                            else -> "Review the terms, choose an optional rating, and begin your first Nastech conversation."
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                item {
                    when (page) {
                        0 -> WelcomeStartVisual()
                        1 -> WelcomeConnectVisual()
                        else -> WelcomeControlVisual(
                            accepted = acceptedTerms,
                            rating = rating,
                            onAcceptedChange = { acceptedTerms = it },
                            onRatingChange = { rating = it },
                        )
                    }
                }

                item {
                    WelcomeNavigation(
                        page = page,
                        acceptedTerms = acceptedTerms,
                        onBack = { page -= 1 },
                        onSkipToTerms = { page = 2 },
                        onNext = {
                            if (page < 2) {
                                page += 1
                            } else {
                                vm.updateSettings(
                                    settings.copy(
                                        onboardingAcceptedVersion = BuildConfig.VERSION_NAME,
                                        onboardingRating = rating.coerceIn(0, 5),
                                    ),
                                )
                                navigator.clearAndNavigate(Screen.Chat(chatId))
                            }
                        },
                    )
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
private fun WelcomeStartVisual() {
    WelcomeIllustration(R.drawable.nastech_onboarding_assistant)
    WelcomeCard(
        title = "A focused conversation, your choices",
        body = "Choose a provider, adjust the glass appearance, and decide which optional tools belong in your chat. Nastech keeps the conversation, approvals, and controls in one connected flow.",
    )
    WelcomeCard(
        title = "Built for visible control",
        body = "The agent’s active work stays in the conversation. You can expand a compact activity batch when you want detail, while approvals remain clear before any sensitive action.",
    )
}

@Composable
private fun WelcomeConnectVisual() {
    WelcomeIllustration(R.drawable.nastech_onboarding_agent)
    WelcomeCard(
        title = "Connect capabilities deliberately",
        body = "Voice Call, skills, workspaces, model providers, browser tasks, and sub-agents are optional. Configure them from Nastech Settings when you are ready.",
    )
    Card(colors = CustomColors.cardColors, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(R.drawable.telegram_logo),
                contentDescription = "Telegram",
                modifier = Modifier.size(46.dp),
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = "Optional Telegram connection",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Configure Bot Token, Telegram ID, access rules, and service controls only if you choose to connect Telegram.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun WelcomeControlVisual(
    accepted: Boolean,
    rating: Int,
    onAcceptedChange: (Boolean) -> Unit,
    onRatingChange: (Int) -> Unit,
) {
    WelcomeIllustration(R.drawable.nastech_onboarding_control)
    WelcomeCard(
        title = "Terms, privacy, and notices",
        body = "Nastech can connect to services and optional tools that you configure. Protect your credentials, review provider terms, and approve sensitive actions only when you understand their effect. Licence and source notices remain available in Settings and the Nastech Research documentation site.",
    )
    Card(colors = CustomColors.cardColors, modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Switch(
                    checked = accepted,
                    onCheckedChange = onAcceptedChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                        checkedTrackColor = MaterialTheme.colorScheme.primary,
                        uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        uncheckedBorderColor = MaterialTheme.colorScheme.outline,
                    ),
                )
                Text(
                    text = "I have reviewed and accept the Nastech Terms, Privacy information, and licence notices.",
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            WelcomeRating(rating = rating, onRatingChange = onRatingChange)
        }
    }
}

@Composable
private fun WelcomeRating(rating: Int, onRatingChange: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "Optional first-launch rating",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "Saved only on this device. You can leave this unrated and continue.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            (1..5).forEach { value ->
                TextButton(onClick = { onRatingChange(if (rating == value) 0 else value) }) {
                    Text(
                        text = if (value <= rating) "★" else "☆",
                        style = MaterialTheme.typography.headlineSmall,
                        color = if (value <= rating) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun WelcomeIllustration(resourceId: Int) {
    Card(colors = CustomColors.cardColors, modifier = Modifier.fillMaxWidth()) {
        Image(
            painter = painterResource(resourceId),
            contentDescription = null,
            modifier = Modifier
                .fillMaxWidth()
                .height(184.dp),
            contentScale = ContentScale.Crop,
        )
    }
}

@Composable
private fun WelcomeNavigation(
    page: Int,
    acceptedTerms: Boolean,
    onBack: () -> Unit,
    onSkipToTerms: () -> Unit,
    onNext: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (page < 2) {
            TextButton(onClick = onSkipToTerms, modifier = Modifier.align(Alignment.End)) {
                Text("Skip to terms")
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (page > 0) {
                OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) {
                    Text("Back")
                }
            } else {
                Spacer(Modifier.width(0.dp))
            }
            Button(
                onClick = onNext,
                enabled = page < 2 || acceptedTerms,
                modifier = Modifier.weight(1f),
            ) {
                Text(if (page < 2) "Next" else "Accept and start")
            }
        }
    }
}

@Composable
private fun WelcomeCard(title: String, body: String) {
    Card(
        colors = CustomColors.cardColors,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
