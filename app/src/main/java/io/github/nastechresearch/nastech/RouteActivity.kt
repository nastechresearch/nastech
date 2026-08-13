package io.github.nastechresearch.nastech

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import coil3.network.cachecontrol.CacheControlCacheStrategy
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import coil3.svg.SvgDecoder
import com.dokar.sonner.Toaster
import com.dokar.sonner.rememberToasterState
import kotlinx.serialization.Serializable
import io.github.nastechresearch.nastech.data.datastore.SettingsStore
import io.github.nastechresearch.nastech.data.datastore.DEFAULT_CODEX_PROVIDER_ID
import io.github.nastechresearch.nastech.data.datastore.DEFAULT_GEMINI_OAUTH_PROVIDER_ID
import io.github.nastechresearch.nastech.data.db.DatabaseMigrationTracker
import io.github.nastechresearch.nastech.data.db.MigrationState
import io.github.nastechresearch.nastech.data.event.AppEvent
import io.github.nastechresearch.nastech.data.event.AppEventBus
import io.github.nastechresearch.nastech.ui.activity.SafeModeActivity
import io.github.nastechresearch.nastech.ui.components.ui.TTSController
import io.github.nastechresearch.nastech.ui.context.LocalNavController
import io.github.nastechresearch.nastech.ui.context.LocalASRState
import io.github.nastechresearch.nastech.ui.context.LocalSettings
import io.github.nastechresearch.nastech.ui.context.LocalSharedTransitionScope
import io.github.nastechresearch.nastech.ui.context.LocalTTSState
import io.github.nastechresearch.nastech.ui.context.LocalToaster
import io.github.nastechresearch.nastech.ui.context.Navigator
import io.github.nastechresearch.nastech.ui.hooks.readBooleanPreference
import io.github.nastechresearch.nastech.ui.hooks.readStringPreference
import io.github.nastechresearch.nastech.ui.hooks.rememberCustomTtsState
import io.github.nastechresearch.nastech.ui.hooks.rememberCustomAsrState
import io.github.nastechresearch.nastech.ui.pages.agent.AgentBridgePage
import io.github.nastechresearch.nastech.ui.pages.assistant.AssistantPage
import io.github.nastechresearch.nastech.ui.pages.assistant.detail.AssistantBasicPage
import io.github.nastechresearch.nastech.ui.pages.assistant.detail.AssistantDetailPage
import io.github.nastechresearch.nastech.ui.pages.assistant.detail.AssistantExtensionsPage
import io.github.nastechresearch.nastech.ui.pages.assistant.detail.AssistantLocalToolPage
import io.github.nastechresearch.nastech.ui.pages.assistant.detail.AssistantMcpPage
import io.github.nastechresearch.nastech.ui.pages.assistant.detail.AssistantMemoryPage
import io.github.nastechresearch.nastech.ui.pages.assistant.detail.AssistantPromptPage
import io.github.nastechresearch.nastech.ui.pages.assistant.detail.AssistantRequestPage
import io.github.nastechresearch.nastech.ui.pages.backup.BackupPage
import io.github.nastechresearch.nastech.ui.pages.chat.ChatPage
import io.github.nastechresearch.nastech.ui.pages.debug.DebugPage
import io.github.nastechresearch.nastech.ui.pages.developer.DeveloperPage
import io.github.nastechresearch.nastech.ui.pages.extensions.ExtensionsPage
import io.github.nastechresearch.nastech.ui.pages.extensions.PromptPage
import io.github.nastechresearch.nastech.ui.pages.extensions.QuickMessagesPage
import io.github.nastechresearch.nastech.ui.pages.extensions.skills.SkillDetailPage
import io.github.nastechresearch.nastech.ui.pages.extensions.skills.SkillsPage
import io.github.nastechresearch.nastech.ui.pages.extensions.workspace.WorkspaceDetailPage
import io.github.nastechresearch.nastech.ui.pages.extensions.workspace.WorkspaceFileEditorPage
import io.github.nastechresearch.nastech.ui.pages.extensions.workspace.WorkspacePage
import io.github.nastechresearch.nastech.ui.pages.extensions.workspace.WorkspaceTerminalPage
import me.rerere.workspace.WorkspaceStorageArea
import io.github.nastechresearch.nastech.ui.pages.favorite.FavoritePage
import io.github.nastechresearch.nastech.ui.pages.history.HistoryPage
import io.github.nastechresearch.nastech.ui.pages.home.HomePage
import io.github.nastechresearch.nastech.ui.pages.imggen.ImageGenPage
import io.github.nastechresearch.nastech.ui.pages.log.LogPage
import io.github.nastechresearch.nastech.ui.pages.search.SearchPage
import io.github.nastechresearch.nastech.ui.pages.setting.SettingAboutPage
import io.github.nastechresearch.nastech.ui.pages.setting.SettingAccessibilityPage
import io.github.nastechresearch.nastech.ui.pages.setting.SettingNotificationsPage
import io.github.nastechresearch.nastech.ui.pages.setting.SettingPermissionsPage
import io.github.nastechresearch.nastech.ui.pages.setting.SettingPreferencesPage
import io.github.nastechresearch.nastech.ui.pages.setting.SettingPreferencesThemePage
import io.github.nastechresearch.nastech.ui.pages.setting.SettingGlassAppearancePage
import io.github.nastechresearch.nastech.ui.pages.setting.SettingPreferencesNotificationPage
import io.github.nastechresearch.nastech.ui.pages.setting.SettingPreferencesGeneralPage
import io.github.nastechresearch.nastech.ui.pages.setting.SettingPreferencesUIPage
import io.github.nastechresearch.nastech.ui.pages.setting.SettingThemePage
import io.github.nastechresearch.nastech.ui.pages.welcome.WelcomePage
import io.github.nastechresearch.nastech.ui.pages.setting.SettingFilesPage
import io.github.nastechresearch.nastech.ui.pages.setting.SettingMcpPage
import io.github.nastechresearch.nastech.ui.pages.setting.SettingModelPage
import io.github.nastechresearch.nastech.ui.pages.setting.SettingPage
import io.github.nastechresearch.nastech.ui.pages.setting.SettingProviderDetailPage
import io.github.nastechresearch.nastech.ui.pages.setting.SettingProviderPage
import io.github.nastechresearch.nastech.ui.pages.setting.SettingSearchDetailPage
import io.github.nastechresearch.nastech.ui.pages.setting.SettingSearchPage
import io.github.nastechresearch.nastech.ui.pages.setting.SettingTTSPage
import io.github.nastechresearch.nastech.ui.pages.setting.SettingSpeechPage
import io.github.nastechresearch.nastech.ui.pages.setting.SettingSubAgentsPage
import io.github.nastechresearch.nastech.ui.pages.setting.SettingTelegramPage
import io.github.nastechresearch.nastech.ui.pages.setting.SettingWebPage
import io.github.nastechresearch.nastech.ui.pages.share.handler.ShareHandlerPage
import io.github.nastechresearch.nastech.ui.pages.stats.StatsPage
import io.github.nastechresearch.nastech.ui.pages.translator.TranslatorPage
import io.github.nastechresearch.nastech.ui.pages.webview.WebViewPage
import io.github.nastechresearch.nastech.ui.theme.LocalDarkMode
import io.github.nastechresearch.nastech.ui.theme.NastechTheme
import io.github.nastechresearch.nastech.utils.CrashHandler
import io.github.nastechresearch.nastech.utils.resolveInitialChatStack
import okhttp3.OkHttpClient
import org.koin.android.ext.android.inject
import org.koin.compose.koinInject
import kotlin.uuid.Uuid

private const val TAG = "RouteActivity"

class RouteActivity : ComponentActivity() {
    companion object {
        const val EXTRA_OPEN_CODEX_SETTINGS = "open_codex_settings"
        const val EXTRA_OPEN_GEMINI_SETTINGS = "open_gemini_settings"
    }

    private val okHttpClient by inject<OkHttpClient>()
    private val settingsStore by inject<SettingsStore>()
    private var navStack: MutableList<NavKey>? = null

    // Volume key listener registry — last registered handler wins
    internal val volumeKeyListeners = mutableListOf<(isVolumeUp: Boolean) -> Boolean>()

    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            val isVolumeUp = when (event.keyCode) {
                KeyEvent.KEYCODE_VOLUME_UP -> true
                KeyEvent.KEYCODE_VOLUME_DOWN -> false
                else -> return super.dispatchKeyEvent(event)
            }
            if (volumeKeyListeners.lastOrNull()?.invoke(isVolumeUp) == true) return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        disableNavigationBarContrast()
        super.onCreate(savedInstanceState)
        if (CrashHandler.hasCrashed(this)) {
            startActivity(Intent(this, SafeModeActivity::class.java))
            finish()
            return
        }
        setContent {
            NastechTheme {
                @OptIn(coil3.annotation.ExperimentalCoilApi::class)
                setSingletonImageLoaderFactory { context ->
                    ImageLoader.Builder(context)
                        .crossfade(true)
                        .components {
                            add(OkHttpNetworkFetcherFactory(
                                callFactory = { okHttpClient },
                                cacheStrategy = { CacheControlCacheStrategy() },
                            ))
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                add(AnimatedImageDecoder.Factory())
                            } else {
                                add(GifDecoder.Factory())
                            }
                            add(SvgDecoder.Factory(scaleToDensity = true))
                        }
                        .build()
                }
                AppRoutes()
            }
        }
    }

    private fun disableNavigationBarContrast() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
    }

    @Composable
    private fun ShareHandler(backStack: MutableList<NavKey>) {
        val shareIntent = remember {
            Intent().apply {
                action = intent?.action
                putExtra(Intent.EXTRA_TEXT, intent?.getStringExtra(Intent.EXTRA_TEXT))
                putExtra(Intent.EXTRA_STREAM, intent?.getStringExtra(Intent.EXTRA_STREAM))
                putExtra(Intent.EXTRA_PROCESS_TEXT, intent?.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT))
            }
        }

        LaunchedEffect(backStack) {
            when (shareIntent.action) {
                Intent.ACTION_SEND -> {
                    val text = shareIntent.getStringExtra(Intent.EXTRA_TEXT) ?: ""
                    val imageUri = shareIntent.getStringExtra(Intent.EXTRA_STREAM)
                    backStack.add(Screen.ShareHandler(text, imageUri))
                }

                Intent.ACTION_PROCESS_TEXT -> {
                    val text = shareIntent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString() ?: ""
                    backStack.add(Screen.ShareHandler(text, null))
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(EXTRA_OPEN_CODEX_SETTINGS, false)) {
            val destination = Screen.SettingProviderDetail(DEFAULT_CODEX_PROVIDER_ID.toString())
            navStack?.let { stack ->
                if (stack.lastOrNull() != destination) stack.add(destination)
            }
            intent.removeExtra(EXTRA_OPEN_CODEX_SETTINGS)
        }
        if (intent.getBooleanExtra(EXTRA_OPEN_GEMINI_SETTINGS, false)) {
            val destination = Screen.SettingProviderDetail(DEFAULT_GEMINI_OAUTH_PROVIDER_ID.toString())
            navStack?.let { stack ->
                if (stack.lastOrNull() != destination) stack.add(destination)
            }
            intent.removeExtra(EXTRA_OPEN_GEMINI_SETTINGS)
        }
        // Navigate to the chat screen if a conversation ID is provided
        intent.getStringExtra("conversationId")?.let { text ->
            navStack?.add(Screen.Chat(text))
            intent.removeExtra("conversationId")
        }
    }

    @OptIn(ExperimentalComposeUiApi::class)
    @Composable
    fun AppRoutes() {
        val toastState = rememberToasterState()
        val settings by settingsStore.settingsFlow.collectAsStateWithLifecycle()
        val tts = rememberCustomTtsState()
        val asr = rememberCustomAsrState()
        val eventBus = koinInject<AppEventBus>()
        LaunchedEffect(tts) {
            eventBus.events.collect { event ->
                when (event) {
                    is AppEvent.Speak -> tts.speak(event.text)
                    else -> {}
                }
            }
        }
        val migrationState by DatabaseMigrationTracker.state.collectAsStateWithLifecycle()

        // Resolve once per composition (not on every recomposition) so a later removeExtra()
        // of "conversationId" can't flip which rememberNavBackStack() branch below gets called.
        val deepLinkConversationId = remember { intent?.getStringExtra("conversationId") }
        val initialChatIds = remember {
            resolveInitialChatStack(
                deepLinkConversationId = deepLinkConversationId,
                createNewOnStart = readBooleanPreference("create_new_conversation_on_start", true),
                lastConversationId = readStringPreference("lastConversationId", null),
                newId = { Uuid.random().toString() },
            )
        }

        val requiresWelcome = settings.onboardingAcceptedVersion != BuildConfig.VERSION_NAME
        val backStack = if (requiresWelcome) {
            rememberNavBackStack(Screen.Welcome(initialChatIds.last()))
        } else if (deepLinkConversationId != null) {
            rememberNavBackStack(Screen.Chat(initialChatIds.last()))
        } else {
            rememberNavBackStack(Screen.Home)
        }
        SideEffect { this@RouteActivity.navStack = backStack }

        LaunchedEffect(backStack) {
            if (intent.getBooleanExtra(EXTRA_OPEN_CODEX_SETTINGS, false)) {
                val destination = Screen.SettingProviderDetail(DEFAULT_CODEX_PROVIDER_ID.toString())
                if (backStack.lastOrNull() != destination) backStack.add(destination)
                intent.removeExtra(EXTRA_OPEN_CODEX_SETTINGS)
            }
            if (intent.getBooleanExtra(EXTRA_OPEN_GEMINI_SETTINGS, false)) {
                val destination =
                    Screen.SettingProviderDetail(DEFAULT_GEMINI_OAUTH_PROVIDER_ID.toString())
                if (backStack.lastOrNull() != destination) backStack.add(destination)
                intent.removeExtra(EXTRA_OPEN_GEMINI_SETTINGS)
            }
            // Deep link was already consumed into the initial back stack above; clear it so a
            // future recreation with the same Intent doesn't re-push it (mirrors how
            // EXTRA_OPEN_CODEX_SETTINGS is cleared above).
            if (deepLinkConversationId != null) {
                intent.removeExtra("conversationId")
            }
        }

        ShareHandler(backStack)

        SharedTransitionLayout {
            CompositionLocalProvider(
                LocalNavController provides Navigator(backStack),
                LocalSharedTransitionScope provides this,
                LocalSettings provides settings,
                LocalToaster provides toastState,
                LocalTTSState provides tts,
                LocalASRState provides asr,
            ) {
                Toaster(
                    state = toastState,
                    darkTheme = LocalDarkMode.current,
                    richColors = true,
                    alignment = Alignment.TopCenter,
                    showCloseButton = true,
                )
                TTSController()
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .semantics { testTagsAsResourceId = true }
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    NavDisplay(
                        backStack = backStack,
                        entryDecorators = listOf(
                            rememberSaveableStateHolderNavEntryDecorator(),
                            rememberViewModelStoreNavEntryDecorator(),
                        ),
                        modifier = Modifier.fillMaxSize(),
                        onBack = { backStack.removeLastOrNull() },
                        transitionSpec = {
                            if (backStack.size == 1) fadeIn() togetherWith fadeOut()
                            else {
                                slideInHorizontally { it } togetherWith
                                    slideOutHorizontally { -it / 2 } + scaleOut(targetScale = 0.7f) + fadeOut()
                            }
                        },
                        popTransitionSpec = {
                            slideInHorizontally { -it / 2 } + scaleIn(initialScale = 0.7f) + fadeIn() togetherWith
                                slideOutHorizontally { it }
                        },
                        predictivePopTransitionSpec = {
                            slideInHorizontally { -it / 2 } + scaleIn(initialScale = 0.7f) + fadeIn() togetherWith
                                slideOutHorizontally { it }
                        },
                        entryProvider = entryProvider {
                            entry<Screen.AgentBridge>(
                                metadata = NavDisplay.transitionSpec { fadeIn() togetherWith fadeOut() }
                                    + NavDisplay.popTransitionSpec { fadeIn() togetherWith fadeOut() }
                            ) {
                                AgentBridgePage()
                            }

                            entry<Screen.Home>(
                                metadata = NavDisplay.transitionSpec { fadeIn() togetherWith fadeOut() }
                                    + NavDisplay.popTransitionSpec { fadeIn() togetherWith fadeOut() }
                            ) {
                                HomePage()
                            }

                            entry<Screen.Chat>(
                                metadata = NavDisplay.transitionSpec { fadeIn() togetherWith fadeOut() }
                                        + NavDisplay.popTransitionSpec { fadeIn() togetherWith fadeOut() }
                            ) { key ->
                                ChatPage(
                                    id = Uuid.parse(key.id),
                                    text = key.text,
                                    files = key.files.map { it.toUri() },
                                    nodeId = key.nodeId?.let { Uuid.parse(it) }
                                )
                            }

                            entry<Screen.Welcome> { key ->
                                WelcomePage(chatId = key.chatId)
                            }

                            entry<Screen.ShareHandler> { key ->
                                ShareHandlerPage(
                                    text = key.text,
                                    image = key.streamUri
                                )
                            }

                            entry<Screen.History> {
                                HistoryPage()
                            }

                            entry<Screen.Favorite> {
                                FavoritePage()
                            }

                            entry<Screen.Assistant> {
                                AssistantPage()
                            }

                            entry<Screen.AssistantDetail> { key ->
                                AssistantDetailPage(key.id)
                            }

                            entry<Screen.AssistantBasic> { key ->
                                AssistantBasicPage(key.id)
                            }

                            entry<Screen.AssistantPrompt> { key ->
                                AssistantPromptPage(key.id)
                            }

                            entry<Screen.AssistantMemory> { key ->
                                AssistantMemoryPage(key.id)
                            }

                            entry<Screen.AssistantRequest> { key ->
                                AssistantRequestPage(key.id)
                            }

                            entry<Screen.AssistantMcp> { key ->
                                AssistantMcpPage(key.id)
                            }

                            entry<Screen.AssistantLocalTool> { key ->
                                AssistantLocalToolPage(key.id)
                            }

                            entry<Screen.AssistantInjections> { key ->
                                AssistantExtensionsPage(key.id)
                            }

                            entry<Screen.Translator> {
                                TranslatorPage()
                            }

                            entry<Screen.Setting> {
                                SettingPage()
                            }

                            entry<Screen.Backup> {
                                BackupPage()
                            }

                            entry<Screen.ImageGen> {
                                ImageGenPage()
                            }

                            entry<Screen.WebView> { key ->
                                WebViewPage(key.url, key.contentId)
                            }

                            entry<Screen.SettingTheme> {
                                SettingThemePage()
                            }

                            entry<Screen.SettingPreferences> {
                                SettingPreferencesPage()
                            }

                            entry<Screen.SettingPreferencesTheme> {
                                SettingPreferencesThemePage()
                            }

                            entry<Screen.SettingGlassAppearance> {
                                SettingGlassAppearancePage()
                            }

                            entry<Screen.SettingPreferencesNotification> {
                                SettingPreferencesNotificationPage()
                            }

                            entry<Screen.SettingPreferencesGeneral> {
                                SettingPreferencesGeneralPage()
                            }

                            entry<Screen.SettingPreferencesUI> {
                                SettingPreferencesUIPage()
                            }

                            entry<Screen.SettingProvider> {
                                SettingProviderPage()
                            }

                            entry<Screen.SettingProviderDetail> { key ->
                                val id = Uuid.parse(key.providerId)
                                SettingProviderDetailPage(id = id)
                            }

                            entry<Screen.SettingModels> {
                                SettingModelPage()
                            }

                            entry<Screen.SettingAbout> {
                                SettingAboutPage()
                            }

                            entry<Screen.SettingSearch> {
                                SettingSearchPage()
                            }

                            entry<Screen.SettingTTS> {
                                SettingTTSPage()
                            }
                            entry<Screen.SettingSearchDetail> { key ->
                                val id = Uuid.parse(key.serviceId)
                                SettingSearchDetailPage(id)
                            }

                            entry<Screen.SettingSpeech> {
                                SettingSpeechPage()
                            }

                            entry<Screen.SettingMcp> {
                                SettingMcpPage()
                            }

                            entry<Screen.SettingSubAgents> {
                                SettingSubAgentsPage()
                            }

                            entry<Screen.SettingFiles> {
                                SettingFilesPage()
                            }

                            entry<Screen.SettingWeb> {
                                SettingWebPage()
                            }

                            entry<Screen.SettingTelegram> {
                                SettingTelegramPage()
                            }

                            entry<Screen.SettingWorkflows> {
                                io.github.nastechresearch.nastech.workflow.ui.WorkflowsScreen()
                            }

                            entry<Screen.WorkflowDetail> { key ->
                                io.github.nastechresearch.nastech.workflow.ui.WorkflowDetailScreen(workflowId = key.id)
                            }

                            entry<Screen.SettingScheduledJobs> {
                                io.github.nastechresearch.nastech.ui.pages.setting.scheduledjobs.ScheduledJobsScreen()
                            }

                            entry<Screen.SettingBrowser> {
                                io.github.nastechresearch.nastech.ui.pages.setting.browser.SettingBrowserPage()
                            }

                            entry<Screen.SettingTermux> {
                                io.github.nastechresearch.nastech.ui.pages.setting.termux.SettingTermuxPage()
                            }

                            entry<Screen.SettingShizuku> {
                                io.github.nastechresearch.nastech.ui.pages.setting.shizuku.SettingShizukuPage()
                            }

                            entry<Screen.ScheduledJobDetail> { key ->
                                io.github.nastechresearch.nastech.ui.pages.setting.scheduledjobs.ScheduledJobDetailScreen(jobId = key.id)
                            }

                            entry<Screen.SettingDoctor> {
                                io.github.nastechresearch.nastech.ui.pages.setting.doctor.DoctorScreen()
                            }

                            entry<Screen.SettingToolApprovals> {
                                io.github.nastechresearch.nastech.ui.pages.setting.SettingToolApprovalsPage()
                            }

                            entry<Screen.SettingAccessibility> {
                                SettingAccessibilityPage()
                            }

                            entry<Screen.SettingNotifications> {
                                SettingNotificationsPage()
                            }

                            entry<Screen.SettingPermissions> {
                                SettingPermissionsPage()
                            }

                            entry<Screen.Developer> {
                                DeveloperPage()
                            }

                            entry<Screen.Debug> {
                                DebugPage()
                            }

                            entry<Screen.Log> {
                                LogPage()
                            }

                            entry<Screen.Extensions> {
                                ExtensionsPage()
                            }

                            entry<Screen.QuickMessages> {
                                QuickMessagesPage()
                            }

                            entry<Screen.Prompts> {
                                PromptPage()
                            }

                            entry<Screen.Skills> {
                                SkillsPage()
                            }

                            entry<Screen.Workspaces> {
                                WorkspacePage()
                            }

                            entry<Screen.WorkspaceDetail> { key ->
                                WorkspaceDetailPage(key.id)
                            }

                            entry<Screen.WorkspaceTerminal> { key ->
                                WorkspaceTerminalPage(key.id)
                            }

                            entry<Screen.WorkspaceFileEditor> { key ->
                                WorkspaceFileEditorPage(
                                    id = key.id,
                                    area = WorkspaceStorageArea.valueOf(key.area),
                                    path = key.path,
                                )
                            }

                            entry<Screen.SkillDetail> { key ->
                                SkillDetailPage(skillName = key.skillName)
                            }

                            entry<Screen.MessageSearch> {
                                SearchPage()
                            }

                            entry<Screen.Stats> {
                                StatsPage()
                            }

                        }
                    )
                    if (BuildConfig.DEBUG) {
                        Text(
                            text = "[Development Mode]",
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                        )
                    }
                    AnimatedVisibility(
                        visible = migrationState is MigrationState.Migrating,
                        enter = fadeIn(),
                        exit = fadeOut(),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        val state = migrationState as? MigrationState.Migrating
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                CircularProgressIndicator()
                                Text(
                                    text = stringResource(R.string.db_migrating),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                if (state != null) {
                                    Text(
                                        text = "v${state.from} → v${state.to}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

sealed interface Screen : NavKey {
    @Serializable
    data object AgentBridge : Screen

    @Serializable
    data object Home : Screen

    @Serializable
    data class Chat(
        val id: String,
        val text: String? = null,
        val files: List<String> = emptyList(),
        val nodeId: String? = null
    ) : Screen

    @Serializable
    data class Welcome(val chatId: String) : Screen

    @Serializable
    data class ShareHandler(val text: String, val streamUri: String? = null) : Screen

    @Serializable
    data object History : Screen

    @Serializable
    data object Favorite : Screen

    @Serializable
    data object Assistant : Screen

    @Serializable
    data class AssistantDetail(val id: String) : Screen

    @Serializable
    data class AssistantBasic(val id: String) : Screen

    @Serializable
    data class AssistantPrompt(val id: String) : Screen

    @Serializable
    data class AssistantMemory(val id: String) : Screen

    @Serializable
    data class AssistantRequest(val id: String) : Screen

    @Serializable
    data class AssistantMcp(val id: String) : Screen

    @Serializable
    data class AssistantLocalTool(val id: String) : Screen

    @Serializable
    data class AssistantInjections(val id: String) : Screen

    @Serializable
    data object Translator : Screen

    @Serializable
    data object Setting : Screen

    @Serializable
    data object Backup : Screen

    @Serializable
    data object ImageGen : Screen

    @Serializable
    data class WebView(val url: String = "", val contentId: String = "") : Screen

    @Serializable
    data object SettingTheme : Screen

    @Serializable
    data object SettingPreferences : Screen

    @Serializable
    data object SettingPreferencesTheme : Screen

    @Serializable
    data object SettingGlassAppearance : Screen

    @Serializable
    data object SettingPreferencesNotification : Screen

    @Serializable
    data object SettingPreferencesGeneral : Screen

    @Serializable
    data object SettingPreferencesUI : Screen

    @Serializable
    data object SettingProvider : Screen

    @Serializable
    data class SettingProviderDetail(val providerId: String) : Screen

    @Serializable
    data object SettingModels : Screen

    @Serializable
    data object SettingAbout : Screen

    @Serializable
    data object SettingSearch : Screen

    @Serializable
    data object SettingTTS : Screen

    @Serializable
    data class SettingSearchDetail(val serviceId: String) : Screen

    @Serializable
    data object SettingSpeech : Screen

    @Serializable
    data object SettingMcp : Screen

    @Serializable
    data object SettingSubAgents : Screen

    @Serializable
    data object SettingFiles : Screen

    @Serializable
    data object SettingWeb : Screen

    @Serializable
    data object SettingTelegram : Screen

    @Serializable
    data object SettingWorkflows : Screen

    @Serializable
    data class WorkflowDetail(val id: String) : Screen

    @Serializable
    data object SettingScheduledJobs : Screen

    @Serializable
    data object SettingBrowser : Screen

    @Serializable
    data object SettingTermux : Screen

    @Serializable
    data object SettingShizuku : Screen

    @Serializable
    data class ScheduledJobDetail(val id: String) : Screen

    @Serializable
    data object SettingDoctor : Screen

    @Serializable
    data object SettingToolApprovals : Screen

    @Serializable
    data object SettingAccessibility : Screen

    @Serializable
    data object SettingNotifications : Screen

    @Serializable
    data object SettingPermissions : Screen

    @Serializable
    data object Developer : Screen

    @Serializable
    data object Debug : Screen

    @Serializable
    data object Log : Screen

    @Serializable
    data object Extensions : Screen

    @Serializable
    data object QuickMessages : Screen

    @Serializable
    data object Prompts : Screen

    @Serializable
    data object Skills : Screen

    @Serializable
    data object Workspaces : Screen

    @Serializable
    data class WorkspaceDetail(val id: String) : Screen

    @Serializable
    data class WorkspaceTerminal(val id: String) : Screen

    @Serializable
    data class WorkspaceFileEditor(val id: String, val area: String, val path: String) : Screen

    @Serializable
    data class SkillDetail(val skillName: String) : Screen

    @Serializable
    data object MessageSearch : Screen

    @Serializable
    data object Stats : Screen

}
