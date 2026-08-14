package io.github.nastechresearch.nastech.di

import kotlinx.serialization.json.Json
import io.github.nastechresearch.nastech.AppScope
import io.github.nastechresearch.nastech.data.ai.AILoggingManager
import io.github.nastechresearch.nastech.data.ai.tools.LocalTools
import io.github.nastechresearch.nastech.data.ai.tools.local.BiometricResultBuffer
import io.github.nastechresearch.nastech.data.ai.tools.local.CameraResultBuffer
import io.github.nastechresearch.nastech.data.event.AppEventBus
import io.github.nastechresearch.nastech.data.ai.tools.local.InteractiveToolStreamer
import io.github.nastechresearch.nastech.data.repository.ScheduledJobRepository
import io.github.nastechresearch.nastech.data.repository.SshHostRepository
import io.github.nastechresearch.nastech.data.repository.TelegramChatRepository
import io.github.nastechresearch.nastech.data.notifications.NotificationListenerPreferences
import io.github.nastechresearch.nastech.data.telegram.TelegramBotClient
import io.github.nastechresearch.nastech.data.telegram.TelegramBotPreferences
import io.github.nastechresearch.nastech.service.ChatService
import io.github.nastechresearch.nastech.service.CronJobScheduler
import io.github.nastechresearch.nastech.utils.EmojiData
import io.github.nastechresearch.nastech.utils.EmojiUtils
import io.github.nastechresearch.nastech.utils.JsonInstant
import io.github.nastechresearch.nastech.utils.SoundEffectPlayer
import io.github.nastechresearch.nastech.utils.UpdateChecker
import io.github.nastechresearch.nastech.web.WebServerManager
import me.rerere.tts.provider.TTSManager
import org.koin.dsl.module

val appModule = module {
    single<Json> { JsonInstant }

    single {
        AppEventBus()
    }

    single { CameraResultBuffer() }
    single { BiometricResultBuffer() }
    // Phase 25 — NFC reader-mode + SAF directory-picker Activity bridges, and the SAF
    // tree-grant store backing the ExternalStorage tools.
    single { io.github.nastechresearch.nastech.data.ai.tools.local.NfcResultBuffer() }
    single { io.github.nastechresearch.nastech.data.ai.tools.local.SafPickerResultBuffer() }
    single { io.github.nastechresearch.nastech.data.storage.StorageVolumeGrantStore(get()) }

    single { ScheduledJobRepository(get<io.github.nastechresearch.nastech.data.db.AppDatabase>().scheduledJobDao()) }
    single { io.github.nastechresearch.nastech.data.repository.ScheduledJobRunRepository(get<io.github.nastechresearch.nastech.data.db.AppDatabase>().scheduledJobRunDao()) }
    single { io.github.nastechresearch.nastech.service.DirectModeActionRunner(get()) }
    single { CronJobScheduler(get(), get()) }
    single { SshHostRepository(get<io.github.nastechresearch.nastech.data.db.AppDatabase>().sshHostDao()) }
    single { TelegramChatRepository(get<io.github.nastechresearch.nastech.data.db.AppDatabase>().telegramChatDao()) }
    single { TelegramBotPreferences(get()) }
    single { io.github.nastechresearch.nastech.browser.BrowserPreferences(get()) }
    single { io.github.nastechresearch.nastech.data.preferences.TermuxPreferences(get()) }
    // Pass 3: Telegram-bound screenshot streamer for headless browser mode. Bound to the
    // [BrowserScreenshotStreamer] interface so [BrowserController.streamScreenshotIfHeadless]
    // can resolve it lazily via Koin without taking a constructor dep — avoids a cycle
    // through TelegramBotClient → TelegramBotPreferences → ... → LocalTools → controller.
    single<io.github.nastechresearch.nastech.browser.BrowserScreenshotStreamer> {
        io.github.nastechresearch.nastech.data.telegram.TelegramBrowserScreenshotStreamer(get(), get(), get())
    }
    // Interactive-tool post-action screenshot streamer for headless mode (Telegram bot /
    // cron / sub-agent). Resolves lazily inside each interactive tool's execute lambda so
    // there's no DI cycle through LocalTools → ChatService → ... → TelegramBotClient.
    single<InteractiveToolStreamer> {
        io.github.nastechresearch.nastech.data.telegram.TelegramInteractiveToolStreamer(get(), get(), get(), get())
    }
    single { io.github.nastechresearch.nastech.data.preferences.ToolApprovalPreferences(get()) }
    single {
        TelegramBotClient(
            tokenProvider = { runCatching { kotlinx.coroutines.runBlocking { get<TelegramBotPreferences>().current().token } }.getOrDefault("") },
            proxyConfigProvider = {
                runCatching {
                    kotlinx.coroutines.runBlocking { get<TelegramBotPreferences>().current() }
                }.getOrDefault(io.github.nastechresearch.nastech.data.telegram.TelegramBotConfig())
            },
        )
    }
    // Phase 24 — Telegram long-poll stall tracker. Shared singleton: TelegramBotService's
    // poll loop calls markUpdate() on every getUpdates; the in-service stall checker and
    // DoctorChecks read it. No cross-dependencies, so no DI-cycle risk.
    single { io.github.nastechresearch.nastech.data.telegram.TelegramPollStallTracker() }
    single { NotificationListenerPreferences(get()) }

    // Phase 13: External Automation Intent API
    single { io.github.nastechresearch.nastech.automation.ExternalAutomationConfig(get()) }
    single {
        io.github.nastechresearch.nastech.automation.ExternalAutomationDispatcher(
            context = get(),
            config = get(),
            chatService = get(),
            conversationRepo = get(),
            settingsStore = get(),
            appScope = get(),
            // Phase 24 — unified AgentRun ledger writer.
            agentRunRepo = get(),
        )
    }

    // Phase 14: Reliability bundle
    single { io.github.nastechresearch.nastech.reliability.GitHubReleaseChecker(get()) }
    single { io.github.nastechresearch.nastech.reliability.BugReportBuilder(get()) }

    // Phase 11: Sub-agents
    single { io.github.nastechresearch.nastech.subagent.SubAgentRegistry() }
    single {
        io.github.nastechresearch.nastech.subagent.SubAgentEngine(
            registry = get(),
            // chatService is resolved lazily inside SubAgentEngine to break the
            // ChatService→LocalTools→SubAgentEngine→ChatService cycle. See SubAgentEngine kdoc.
            conversationRepo = get(),
            settingsStore = get(),
            appScope = get(),
            // Phase 24 — unified AgentRun ledger writer. No DI cycle: AgentRunRepository
            // depends only on its DAO.
            agentRunRepo = get(),
        )
    }

    // Phase 16: Skill URL-import
    single {
        io.github.nastechresearch.nastech.skills.SkillUrlImporter(
            skillManager = get<io.github.nastechresearch.nastech.data.files.SkillManager>(),
        )
    }

    // Shared repository-directory importer for explicit skill installation and refresh.
    single {
        io.github.nastechresearch.nastech.skills.GitHubSkillImporter(
            skillManager = get<io.github.nastechresearch.nastech.data.files.SkillManager>(),
        )
    }

    // Phase 19B: Skill isolation tester. Eager construction is safe here — ChatService
    // doesn't reach back into SkillTestRunner anywhere, so no DI cycle.
    single {
        io.github.nastechresearch.nastech.skills.SkillTestRunner(
            chatService = get(),
            skillManager = get(),
            conversationRepo = get(),
            settingsStore = get(),
        )
    }

    // Phase 18: JS skills (run_js + secrets store)
    single { io.github.nastechresearch.nastech.skills.js.JsSkillRunner(get()) }
    single { io.github.nastechresearch.nastech.skills.js.SkillSecretsStore(get()) }

    // Phase 12: Workflows
    single {
        io.github.nastechresearch.nastech.workflow.repository.WorkflowRepository(
            workflowDao = get<io.github.nastechresearch.nastech.data.db.AppDatabase>().workflowDao(),
            workflowRunDao = get<io.github.nastechresearch.nastech.data.db.AppDatabase>().workflowRunDao(),
        )
    }
    single { io.github.nastechresearch.nastech.workflow.condition.ContextProvider(get()) }
    single { io.github.nastechresearch.nastech.workflow.execution.WorkflowActionRunner() }
    single {
        io.github.nastechresearch.nastech.workflow.execution.WorkflowEngine(
            repository = get(),
            settingsStore = get(),
            contextProvider = get(),
            actionRunner = get(),
        ).also { engine ->
            // Bridge for the repo to notify the engine on delete so the engine's per-workflow
            // lock map doesn't leak. Lazy because both singletons have to exist first.
            get<io.github.nastechresearch.nastech.workflow.repository.WorkflowRepository>().bindEngine(engine)
        }
    }
    single {
        io.github.nastechresearch.nastech.workflow.trigger.TriggerRegistry(
            context = get(),
            appScope = get(),
            workflowRepository = get(),
        )
    }

    single { io.github.nastechresearch.nastech.data.keyboard.KeyboardApiClient(get()) }

    single {
        LocalTools(
            context = get(),
            eventBus = get(),
            cameraResultBuffer = get(),
            biometricResultBuffer = get(),
            scheduledJobRepository = get(),
            scheduledJobRunRepository = get(),
            cronJobScheduler = get(),
            settingsStore = get(),
            sshHostRepository = get(),
            telegramBotPreferences = get(),
            telegramBotClient = get(),
            notificationListenerPreferences = get(),
            mcpManager = get(),
            externalAutomationConfig = get(),
            gitHubReleaseChecker = get(),
            bugReportBuilder = get(),
            subAgentEngine = get(),
            subAgentRegistry = get(),
            conversationRepo = get(),
            workflowRepository = get(),
            workflowEngine = get(),
            skillUrlImporter = get(),
            skillManager = get(),
            jsSkillRunner = get(),
            skillSecretsStore = get(),
            browserPreferences = get(),
            termuxPreferences = get(),
            interactiveToolStreamer = get(),
            nfcResultBuffer = get(),
            safPickerResultBuffer = get(),
            storageVolumeGrantStore = get(),
            okHttpClient = get(),
            keyboardApiClient = get(),
        )
    }

    single {
        UpdateChecker(get())
    }

    single {
        AppScope()
    }

    single<EmojiData> {
        EmojiUtils.loadEmoji(get())
    }

    single {
        me.rerere.tts.kokoro.KokoroPackageManager(get())
    }

    single {
        me.rerere.asr.LocalAsrPackageManager(get())
    }

    single {
        TTSManager(get(), get())
    }

    single {
        SoundEffectPlayer(get())
    }

    single {
        AILoggingManager(get(), get())
    }

    // Phase 22A: Local-LLM on-device providers
    single { me.rerere.locallm.LocalRuntimePreferences(get()) }
    single { me.rerere.locallm.litert.LiteRtRuntime(get()) }
    single { me.rerere.llamacpp.LlamaCppRuntime() }

    single {
        ChatService(
            context = get(),
            appScope = get(),
            appEventBus = get(),
            settingsStore = get(),
            conversationRepo = get(),
            memoryRepository = get(),
            generationHandler = get(),
            templateTransformer = get(),
            providerManager = get(),
            localTools = get(),
            mcpManager = get(),
            filesManager = get(),
            skillManager = get(),
            toolApprovalPreferences = get(),
            workspaceRepository = get(),
            folderRepository = get()
        )
    }

    single {
        WebServerManager(
            context = get(),
            appScope = get(),
            chatService = get(),
            conversationRepo = get(),
            folderRepo = get(),
            settingsStore = get(),
            filesManager = get()
        )
    }

    single {
        io.github.nastechresearch.nastech.ui.pages.setting.doctor.DoctorChecks(
            context = get(),
            settingsStore = get(),
            telegramPrefs = get(),
            workflowRepository = get(),
            scheduledJobRepository = get(),
            scheduledJobRunRepository = get(),
            conversationRepository = get(),
            database = get(),
            // Pass 3: surface the browser write-tools-enabled INFO row + profile-dir AutoFix.
            browserPreferences = get(),
            // Phase 25: surface the SAF granted-directories live count.
            storageVolumeGrantStore = get(),
            // LiteRT accelerator status row in the Doctor: shows the persisted backend
            // decision so a silent GPU -> CPU fallback is visible.
            localRuntimePreferences = get(),
            // Doctor refresh: skills.* and service.mcp_servers rows.
            skillManager = get(),
            mcpManager = get(),
        )
    }
}
