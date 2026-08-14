package io.github.nastechresearch.nastech.ui.components.message

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowDown01
import me.rerere.hugeicons.stroke.ArrowUp01
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.LanguageCircle
import io.github.nastechresearch.nastech.R
import io.github.nastechresearch.nastech.ui.components.richtext.MarkdownBlock
import java.util.Locale

private data class TranslationLanguage(
    val locale: Locale,
    val displayName: String,
    val searchTerms: String = displayName,
)

private data class TranslationLanguageGroup(
    val title: String,
    val languages: List<TranslationLanguage>,
)

private fun translationLanguageGroups(): List<TranslationLanguageGroup> = listOf(
    TranslationLanguageGroup(
        title = "East Africa",
        languages = listOf(
            TranslationLanguage(Locale.forLanguageTag("sw"), "🇹🇿 Kiswahili (Swahili)", "swahili kiswahili east africa bantu sw"),
            TranslationLanguage(Locale.forLanguageTag("rw"), "🇷🇼 Kinyarwanda", "kinyarwanda rwanda east africa bantu rw"),
            TranslationLanguage(Locale.forLanguageTag("rn"), "🇧🇮 Kirundi", "kirundi burundi east africa bantu rn"),
            TranslationLanguage(Locale.forLanguageTag("lg"), "🇺🇬 Luganda", "luganda uganda east africa bantu lg"),
            TranslationLanguage(Locale.forLanguageTag("om"), "🇪🇹 Afaan Oromo", "oromo afaan oromo ethiopia east africa om"),
            TranslationLanguage(Locale.forLanguageTag("ti"), "🇪🇷 Tigrinya", "tigrinya eritrea ethiopia east africa ti"),
            TranslationLanguage(Locale.forLanguageTag("am"), "🇪🇹 Amharic", "amharic ethiopia east africa am"),
        ),
    ),
    TranslationLanguageGroup(
        title = "Southern Africa",
        languages = listOf(
            TranslationLanguage(Locale.forLanguageTag("zu"), "🇿🇦 isiZulu", "zulu isizulu south africa southern africa bantu zu"),
            TranslationLanguage(Locale.forLanguageTag("xh"), "🇿🇦 isiXhosa", "xhosa isixhosa south africa southern africa bantu xh"),
            TranslationLanguage(Locale.forLanguageTag("st"), "🇱🇸 Sesotho", "sotho sesotho lesotho southern africa bantu st"),
            TranslationLanguage(Locale.forLanguageTag("tn"), "🇧🇼 Setswana", "tswana setswana botswana southern africa bantu tn"),
            TranslationLanguage(Locale.forLanguageTag("ss"), "🇸🇿 siSwati", "swati siswati eswatini southern africa bantu ss"),
            TranslationLanguage(Locale.forLanguageTag("nr"), "🇿🇦 isiNdebele", "ndebele isindebele south africa southern africa bantu nr"),
            TranslationLanguage(Locale.forLanguageTag("ny"), "🇲🇼 Chichewa", "chichewa chewa malawi southern africa bantu ny"),
            TranslationLanguage(Locale.forLanguageTag("sn"), "🇿🇼 Shona", "shona zimbabwe southern africa bantu sn"),
        ),
    ),
    TranslationLanguageGroup(
        title = "West Africa",
        languages = listOf(
            TranslationLanguage(Locale.forLanguageTag("yo"), "🇳🇬 Yoruba", "yoruba nigeria west africa yo"),
            TranslationLanguage(Locale.forLanguageTag("ig"), "🇳🇬 Igbo", "igbo nigeria west africa ig"),
            TranslationLanguage(Locale.forLanguageTag("ha"), "🇳🇬 Hausa", "hausa nigeria niger west africa ha"),
            TranslationLanguage(Locale.forLanguageTag("ak"), "🇬🇭 Akan (Twi)", "akan twi ghana west africa ak"),
            TranslationLanguage(Locale.forLanguageTag("ee"), "🇬🇭 Ewe", "ewe ghana togo west africa ee"),
            TranslationLanguage(Locale.forLanguageTag("wo"), "🇸🇳 Wolof", "wolof senegal west africa wo"),
        ),
    ),
    TranslationLanguageGroup(
        title = "North Africa",
        languages = listOf(
            TranslationLanguage(Locale.forLanguageTag("ar"), "🇪🇬 Arabic", "arabic north africa middle east ar"),
            TranslationLanguage(Locale.forLanguageTag("ar-MA"), "🇲🇦 Moroccan Arabic", "darija moroccan arabic morocco north africa ar-ma"),
            TranslationLanguage(Locale.forLanguageTag("zgh"), "🇲🇦 Tamazight", "tamazight amazigh berber morocco north africa zgh"),
            TranslationLanguage(Locale.forLanguageTag("kab"), "🇩🇿 Kabyle", "kabyle algeria north africa kab"),
        ),
    ),
    TranslationLanguageGroup(
        title = "Universal languages",
        languages = listOf(
            TranslationLanguage(Locale.ENGLISH, "🇬🇧 English", "english en universal"),
            TranslationLanguage(Locale.FRENCH, "🇫🇷 Français", "french francais fr universal"),
            TranslationLanguage(Locale.forLanguageTag("pt-BR"), "🇧🇷 Português", "portuguese portugues pt brazil universal"),
            TranslationLanguage(Locale.forLanguageTag("es-ES"), "🇪🇸 Español", "spanish espanol es universal"),
            TranslationLanguage(Locale.GERMAN, "🇩🇪 Deutsch", "german deutsch de universal"),
            TranslationLanguage(Locale.ITALIAN, "🇮🇹 Italiano", "italian it universal"),
            TranslationLanguage(Locale.SIMPLIFIED_CHINESE, "🇨🇳 Simplified Chinese", "chinese simplified mandarin zh universal"),
            TranslationLanguage(Locale.TRADITIONAL_CHINESE, "🇹🇼 Traditional Chinese", "chinese traditional mandarin zh universal"),
            TranslationLanguage(Locale.JAPANESE, "🇯🇵 Japanese", "japanese ja universal"),
            TranslationLanguage(Locale.KOREAN, "🇰🇷 Korean", "korean ko universal"),
            TranslationLanguage(Locale.forLanguageTag("fa"), "🇮🇷 فارسی", "persian farsi iran fa universal"),
            TranslationLanguage(Locale.forLanguageTag("ur"), "🇵🇰 اردو", "urdu pakistan ur universal"),
        ),
    ),
)

@Composable
fun LanguageSelectionDialog(
    onLanguageSelected: (Locale) -> Unit,
    onClearTranslation: () -> Unit = {},
    onDismissRequest: () -> Unit,
) {
    val languageGroups = remember { translationLanguageGroups() }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val normalizedQuery = searchQuery.trim().lowercase(Locale.ROOT)
    val matchingGroups = languageGroups.mapNotNull { group ->
        val matches = group.languages.filter { language ->
            normalizedQuery.isBlank() ||
                language.displayName.lowercase(Locale.ROOT).contains(normalizedQuery) ||
                language.searchTerms.lowercase(Locale.ROOT).contains(normalizedQuery) ||
                language.locale.toLanguageTag().lowercase(Locale.ROOT).contains(normalizedQuery)
        }
        group.takeIf { matches.isNotEmpty() }?.copy(languages = matches)
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.translation_language_selection_title),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = "African languages are shown first, followed by universal languages.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("Search language, region, or code") },
            )

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                matchingGroups.forEach { group ->
                    item(key = "heading-${group.title}") {
                        Text(
                            text = group.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                        )
                    }
                    items(group.languages, key = { it.locale.toLanguageTag() }) { language ->
                        TranslationLanguageCard(
                            language = language,
                            onClick = { onLanguageSelected(language.locale) },
                        )
                    }
                }
                if (matchingGroups.isEmpty()) {
                    item {
                        Text(
                            text = "No language matches your search.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 20.dp),
                        )
                    }
                }
                item {
                    Card(
                        onClick = onClearTranslation,
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier
                                .padding(16.dp)
                                .fillMaxWidth(),
                        ) {
                            Icon(imageVector = HugeIcons.Cancel01, contentDescription = null)
                            Text(
                                text = stringResource(R.string.translation_clear),
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TranslationLanguageCard(
    language: TranslationLanguage,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
        ) {
            Icon(
                imageVector = HugeIcons.LanguageCircle,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(text = language.displayName, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = language.locale.toLanguageTag(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun CollapsibleTranslationText(
    content: String,
    onClickCitation: (String) -> Unit
) {
    if (content.isNotBlank()) {
        var isCollapsed by remember { mutableStateOf(false) }

        Spacer(modifier = Modifier.height(12.dp))

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 8.dp),
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
        )

        // Translation title and collapse button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = HugeIcons.LanguageCircle,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = stringResource(R.string.translation_text),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // 折叠/展开按钮
            IconButton(
                onClick = { isCollapsed = !isCollapsed },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = if (isCollapsed) HugeIcons.ArrowDown01 else HugeIcons.ArrowUp01,
                    contentDescription = if (isCollapsed) stringResource(R.string.expand_translation) else stringResource(
                        R.string.collapse_translation
                    ),
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Translation content (collapsible)
        AnimatedVisibility(
            visible = !isCollapsed,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                ),
                shape = MaterialTheme.shapes.medium
            ) {
                // Check if it's loading state
                val isTranslating = content == stringResource(R.string.translating)

                if (isTranslating) {
                    // Show loading animation for translation
                    Row(
                        modifier = Modifier
                            .padding(12.dp)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )

                        val infiniteTransition = rememberInfiniteTransition(label = "loading")
                        val alpha by infiniteTransition.animateFloat(
                            initialValue = 0.3f,
                            targetValue = 1f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(1000, easing = LinearEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "alpha"
                        )

                        Text(
                            text = stringResource(R.string.translating),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.graphicsLayer(alpha = alpha)
                        )
                    }
                } else {
                    // Show normal translation content
                    MarkdownBlock(
                        content = content,
                        onClickCitation = onClickCitation,
                        modifier = Modifier
                            .padding(12.dp)
                            .animateContentSize()
                    )
                }
            }
        }
    }
}
