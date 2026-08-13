package io.github.nastechresearch.nastech.ui.components.message

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import io.github.nastechresearch.nastech.Screen
import io.github.nastechresearch.nastech.ui.components.webview.WebContent
import io.github.nastechresearch.nastech.ui.components.webview.WebView
import io.github.nastechresearch.nastech.ui.components.webview.WebViewState
import io.github.nastechresearch.nastech.ui.context.LocalNavController

private val sharedUrlPattern = Regex("""https?://[^\\s<>]+""")

@Composable
fun ChatRichPreview(text: String) {
    val url = remember(text) { sharedUrlPattern.find(text)?.value?.trimEnd('.', ',', ')', ']') } ?: return
    val navigator = LocalNavController.current
    val videoId = remember(url) { youtubeVideoId(url) }
    if (videoId != null) {
        val state = remember(videoId) {
            WebViewState(
                WebContent.Data(
                    data = "<html><body style='margin:0;background:#000'><iframe width='100%' height='100%' src='https://www.youtube.com/embed/$videoId' frameborder='0' allow='autoplay; encrypted-media; picture-in-picture' allowfullscreen></iframe></body></html>",
                    baseUrl = "https://www.youtube.com/",
                    mimeType = "text/html",
                )
            )
        }
        Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh), modifier = Modifier.fillMaxWidth()) {
            WebView(state, Modifier.fillMaxWidth().height(210.dp).clip(RoundedCornerShape(18.dp)))
        }
    } else {
        Card(onClick = { navigator.navigate(Screen.WebView(url = url)) }, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh), modifier = Modifier.fillMaxWidth()) {
            Text(url, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(14.dp))
        }
    }
}

private fun youtubeVideoId(url: String): String? {
    val short = Regex("""youtu\.be/([A-Za-z0-9_-]{6,})""").find(url)?.groupValues?.getOrNull(1)
    val standard = Regex("""youtube\.com/watch\?[^#]*v=([A-Za-z0-9_-]{6,})""").find(url)?.groupValues?.getOrNull(1)
    val embed = Regex("""youtube\.com/(?:embed|shorts)/([A-Za-z0-9_-]{6,})""").find(url)?.groupValues?.getOrNull(1)
    return short ?: standard ?: embed
}
