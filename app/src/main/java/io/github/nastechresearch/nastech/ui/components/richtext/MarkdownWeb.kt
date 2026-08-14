package io.github.nastechresearch.nastech.ui.components.richtext

import android.content.Context
import androidx.compose.material3.ColorScheme
import io.github.nastechresearch.nastech.utils.toCssHex
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.html.HtmlGenerator
import org.intellij.markdown.parser.MarkdownParser
import org.jsoup.Jsoup
import org.jsoup.safety.Safelist

private val previewFlavour by lazy {
    GFMFlavourDescriptor(makeHttpsAutoLinks = true, useSafeLinks = true)
}

private val previewParser by lazy { MarkdownParser(previewFlavour) }

/**
 * Converts assistant Markdown to a self-contained artifact document. The native parser and
 * sanitiser run before the WebView is created, so generated Markdown cannot inject scripts,
 * frames, event handlers, or browser-privileged markup into the preview surface.
 */
fun buildMarkdownPreviewHtml(context: Context, markdown: String, colorScheme: ColorScheme): String {
    val tree = previewParser.buildMarkdownTreeFromString(markdown)
    val generatedHtml = HtmlGenerator(markdown, tree, previewFlavour).generateHtml()
    val safeHtml = Jsoup.clean(
        generatedHtml,
        "",
        Safelist.relaxed()
            .addTags("table", "thead", "tbody", "tr", "th", "td", "pre", "code", "hr")
            .addAttributes("code", "class"),
    )
    val htmlTemplate = context.assets.open("html/nastech_artifact.html").bufferedReader().use { it.readText() }

    return htmlTemplate
        .replace("{{CONTENT_HTML}}", safeHtml)
        .replace("{{BACKGROUND_COLOR}}", colorScheme.background.toCssHex())
        .replace("{{ON_BACKGROUND_COLOR}}", colorScheme.onBackground.toCssHex())
        .replace("{{SURFACE_COLOR}}", colorScheme.surface.toCssHex())
        .replace("{{ON_SURFACE_COLOR}}", colorScheme.onSurface.toCssHex())
        .replace("{{SURFACE_VARIANT_COLOR}}", colorScheme.surfaceVariant.toCssHex())
        .replace("{{ON_SURFACE_VARIANT_COLOR}}", colorScheme.onSurfaceVariant.toCssHex())
        .replace("{{PRIMARY_COLOR}}", colorScheme.primary.toCssHex())
        .replace("{{OUTLINE_COLOR}}", colorScheme.outline.toCssHex())
        .replace("{{OUTLINE_VARIANT_COLOR}}", colorScheme.outlineVariant.toCssHex())
}
