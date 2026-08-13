package io.github.nastechresearch.nastech.ui.components.message

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.github.nastechresearch.nastech.Screen
import io.github.nastechresearch.nastech.ui.components.ui.Favicon
import io.github.nastechresearch.nastech.ui.components.webview.WebContent
import io.github.nastechresearch.nastech.ui.components.webview.WebView
import io.github.nastechresearch.nastech.ui.components.webview.WebViewState
import io.github.nastechresearch.nastech.ui.context.LocalNavController
import io.github.nastechresearch.nastech.ui.theme.CustomColors
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

private val sharedUrlPattern = Regex("""https?://[^\\s<>]+""")
private val directImagePattern = Regex("""(?i)\\.(?:avif|gif|jpe?g|png|webp)(?:[?#].*)?$""")

/**
 * A compact media continuation for assistant messages. It intentionally remains in the message
 * flow: direct image links render as images, YouTube is playable in a quiet 16:9 card, and other
 * URLs receive a source-logo card that opens in Nastech's retained WebView route.
 */
@Composable
fun ChatRichPreview(text: String) {
    val url = remember(text) { sharedUrlPattern.find(text)?.value?.trimEnd('.', ',', ')', ']') } ?: return
    val navigator = LocalNavController.current
    val videoId = remember(url) { youtubeVideoId(url) }

    when {
        videoId != null -> InChatYoutubePreview(videoId = videoId)
        directImagePattern.containsMatchIn(url) -> InChatImagePreview(url = url)
        else -> InChatLinkPreview(url = url, onClick = { navigator.navigate(Screen.WebView(url = url)) })
    }
}

@Composable
private fun InChatYoutubePreview(videoId: String) {
    val state = remember(videoId) {
        WebViewState(
            WebContent.Data(
                data = """
                    <html><body style='margin:0;background:#030406;overflow:hidden'>
                    <iframe width='100%' height='100%' src='https://www.youtube.com/embed/$videoId?playsinline=1&rel=0'
                    title='YouTube video player' frameborder='0'
                    allow='accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share'
                    allowfullscreen></iframe></body></html>
                """.trimIndent(),
                baseUrl = "https://www.youtube.com/",
                mimeType = "text/html",
            ),
        )
    }
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CustomColors.cardColors,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            WebView(
                state = state,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(202.dp)
                    .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)),
            )
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Favicon(url = "https://www.youtube.com", modifier = Modifier.size(20.dp))
                Text(
                    text = "YouTube · playing in Nastech",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun InChatImagePreview(url: String) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CustomColors.cardColors,
        modifier = Modifier.fillMaxWidth(),
    ) {
        AsyncImage(
            model = url,
            contentDescription = "Linked image",
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .clip(RoundedCornerShape(22.dp)),
            contentScale = ContentScale.Crop,
        )
    }
}

@Composable
private fun InChatLinkPreview(url: String, onClick: () -> Unit) {
    val parsed = remember(url) { url.toHttpUrlOrNull() }
    val host = parsed?.host?.removePrefix("www.") ?: "Linked content"
    val path = parsed?.encodedPath?.takeIf { it.isNotBlank() && it != "/" } ?: "Open securely inside Nastech"
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        colors = CustomColors.cardColors,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Favicon(url = url, modifier = Modifier.size(34.dp), shape = MaterialTheme.shapes.small)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = host,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = path,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun youtubeVideoId(url: String): String? {
    val short = Regex("""youtu\\.be/([A-Za-z0-9_-]{6,})""").find(url)?.groupValues?.getOrNull(1)
    val standard = Regex("""youtube\\.com/watch\\?[^#]*v=([A-Za-z0-9_-]{6,})""").find(url)?.groupValues?.getOrNull(1)
    val embed = Regex("""youtube\\.com/(?:embed|shorts)/([A-Za-z0-9_-]{6,})""").find(url)?.groupValues?.getOrNull(1)
    return short ?: standard ?: embed
}
