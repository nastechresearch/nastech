package io.github.nastechresearch.nastech.ui.pages.welcome

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.nastechresearch.nastech.R
import io.github.nastechresearch.nastech.data.datastore.GlassSurface
import io.github.nastechresearch.nastech.ui.theme.glassContentColor
import io.github.nastechresearch.nastech.ui.theme.glassSurface

private data class WelcomeSlide(
    val label: String,
    val title: String,
    val body: String,
)

private val WELCOME_SLIDES = listOf(
    WelcomeSlide(
        label = "01 · Nastech",
        title = "A calmer way to work with AI.",
        body = "Nastech combines conversation, useful phone tools, and clear controls in one focused workspace.",
    ),
    WelcomeSlide(
        label = "02 · Cloud-first voice",
        title = "Choose independent speech engines.",
        body = "Text-to-speech and transcription stay separate. Connect ElevenLabs or another cloud provider you trust, then manage each from Speech settings.",
    ),
    WelcomeSlide(
        label = "03 · Your provider, your choice",
        title = "Connect, discover, and create.",
        body = "Use OpenRouter, Ollama Cloud, OpenCode Zen, or your own compatible provider. Live catalogues help you find explicitly listed free models.",
    ),
)

/** A first-install-only introduction that overlays the normal app; it is not a persistent home screen. */
@Composable
fun NastechWelcomeOverlay(
    modifier: Modifier = Modifier,
    onComplete: () -> Unit,
) {
    var page by remember { mutableIntStateOf(0) }
    val transition = rememberInfiniteTransition(label = "welcome-breathe")
    val glowScale by transition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(2_800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "welcome-glow-scale",
    )
    val glowAlpha by transition.animateFloat(
        initialValue = 0.18f,
        targetValue = 0.42f,
        animationSpec = infiniteRepeatable(
            animation = tween(2_800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "welcome-glow-alpha",
    )
    val surface = glassSurface(GlassSurface.SETTINGS, MaterialTheme.colorScheme.surface)
    val contentColor = glassContentColor(GlassSurface.SETTINGS, MaterialTheme.colorScheme.surface)

    Surface(
        modifier = modifier.fillMaxSize(),
        color = surface.container,
        contentColor = contentColor,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF76B8FF).copy(alpha = 0.14f),
                            Color(0xFF83D9B7).copy(alpha = 0.05f),
                            Color.Black,
                        ),
                    ),
                ),
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 72.dp)
                    .size(228.dp)
                    .scale(glowScale)
                    .alpha(glowAlpha)
                    .clip(CircleShape)
                    .background(Color(0xFF76B8FF)),
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 28.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onComplete) {
                        Text("Skip")
                    }
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(
                        modifier = Modifier.size(124.dp),
                        shape = RoundedCornerShape(36.dp),
                        color = Color.Black,
                        tonalElevation = 8.dp,
                    ) {
                        androidx.compose.foundation.Image(
                            painter = painterResource(R.mipmap.ic_launcher_foreground),
                            contentDescription = "Nastech",
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                    Spacer(Modifier.height(28.dp))

                    AnimatedContent(
                        targetState = WELCOME_SLIDES[page],
                        transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(180)) },
                        label = "welcome-slide",
                    ) { slide ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = slide.label,
                                style = MaterialTheme.typography.labelLarge,
                                color = Color(0xFF83D9B7),
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = slide.title,
                                style = MaterialTheme.typography.headlineMedium,
                                color = contentColor,
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = slide.body,
                                style = MaterialTheme.typography.bodyLarge,
                                color = contentColor.copy(alpha = 0.78f),
                            )
                        }
                    }
                }

                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 18.dp),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        WELCOME_SLIDES.indices.forEach { index ->
                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 4.dp)
                                    .width(if (index == page) 28.dp else 8.dp)
                                    .height(8.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (index == page) Color(0xFF76B8FF) else contentColor.copy(alpha = 0.18f),
                                    ),
                            )
                        }
                    }
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF76B8FF),
                            contentColor = Color.Black,
                        ),
                        onClick = {
                            if (page == WELCOME_SLIDES.lastIndex) onComplete() else page += 1
                        },
                    ) {
                        Text(if (page == WELCOME_SLIDES.lastIndex) "Start with Nastech" else "Continue", fontSize = 16.sp)
                    }
                }
            }
        }
    }
}
