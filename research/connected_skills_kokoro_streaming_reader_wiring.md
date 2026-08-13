# Nastech Connected Skills, Kokoro, and Streaming Reader Wiring Research

## Scope

This research maps the current Nastech implementation seams for four connected extensions: direct Git skill installation and refresh, curated tool-catalogue maintenance, a downloadable on-device Kokoro voice package, and a reader that progressively reveals text while speech is active. It also records the visual-system boundary: the supplied **Black Silence** HTML references are the whole-app design system and must replace legacy opaque or generic glass materials through shared primitives and page-level components.

## Existing connected architecture

| Concern | Existing Nastech seam | Required extension |
|---|---|---|
| Git skill installation | `SkillsVM.importSkillFromGitHub()` enumerates a public repository directory through GitHub Contents, reads `SKILL.md`, downloads the file tree, and uses `SkillManager.saveSkillFilesAtomically()` | Reuse this exact atomic fetch-and-replace path for an explicit **Update from source** action. |
| Raw skill import | `SkillUrlImporter` validates `http(s)`, blocks loopback targets, rejects HTML landing pages, caps body size, detects compatible skill formats, and persists `source-url` frontmatter | Retain the guard for single-file raw URLs. Do not fetch or execute repository code automatically. |
| Installed-skill inspection | `SkillDetailPage` and `SkillDetailVM` own the current editable file tree and skill metadata | Add a source label, update status, and a user-triggered **Refresh from Git/source** action here. |
| Tool access | Built-in tools are registered in the application tool registry and external or mutating actions use existing approvals | A skills catalogue refresh may add *descriptions and import sources* only. It must not add arbitrary executable tools, bypass approvals, or run third-party scripts. |
| TTS selection | `TTSProviderSetting` is a serializable sealed type; `TTSManager` dispatches every selected type to a concrete provider; `TTSProviderConfigure` creates and configures provider entries | Add a `KokoroLocal` setting subtype, registry branch, configuration branch, package status, selected voice, rate, and model-package version. |
| Local speech package | The app has no ONNX or Sherpa TTS runtime dependency today; current providers produce buffered `AudioChunk` flows | Add an Android local inference runtime plus a package manager that downloads, verifies, installs, selects, and removes the model assets in app-owned storage. |
| Playback and reader | `TtsController` chunks text, prefetches and synthesizes audio, then plays sequentially. `ScreenReader` currently animates words on a timer because it receives only chunk progress. | Add a first-class reader-progress model: actual range updates where available, chunk-aware phrase progress otherwise, and an honest “progressive text” state rather than claiming universal word-perfect timing. |

## Direct Git skill lifecycle

The repository importer already provides the correct safe primitive: fetch a repository directory chosen by the user, obtain its `SKILL.md` and sidecar text files, then atomically replace that one installed skill directory. Refresh is therefore not a new capability path; it is an update action for a stored `source-url`.

| User action | Nastech behavior | Safety constraint |
|---|---|---|
| Import from Git | User enters a public GitHub repository directory that contains `SKILL.md`; Nastech shows fetched metadata before installation. | Only HTTPS public GitHub paths or raw HTTPS skill documents; no local, loopback, SSH, or opaque redirect targets. |
| Review installed skill | Skill detail displays its source label, last local update time, and the current editable file tree. | Source provenance remains visible after import. |
| Refresh from source | User taps refresh; Nastech re-fetches the original selected directory, validates the new tree, previews changed paths and source metadata, then atomically replaces only after a confirmation. | Never background-pull, silently overwrite local edits, or execute scripts in the skill repository. |
| Refresh catalogue | User taps **Refresh sources** on the catalogue. Nastech updates a signed/curated index of source-linked entries, not installed skills. | Entries remain descriptive until the user taps Install; any tool referenced by a skill remains subject to existing tool controls and approvals. |

A Git repository is not itself a safe execution format. The app should import and display skill text as user-controlled instruction content. Any actual device action remains on the existing tool path, which retains its approval gates.

## Local Kokoro package design

Kokoro requires more than a single model file: a compatible ONNX model, voice embeddings, tokenizer or pronunciation resources, and an on-device runtime. The public ONNX Community model card documents Apache-2.0 Kokoro weights and lists a standard FP32 model at 326 MB, FP16 at 163 MB, quantized at 92.4 MB, and mixed precision at 86 MB.[1] The `kokoro-onnx` project similarly describes an approximately 300 MB standard package and an approximately 80 MB quantized package, plus a voices binary; it also notes the need for grapheme-to-phoneme processing for v1.0.[2]

The initial Nastech package should default to the quantized mobile option rather than force a nominal 400 MB download. A higher-fidelity package may be offered later only after device-capability validation. ONNX Runtime officially supports Android Java/Kotlin through `onnxruntime-android`; its mobile guidance warns that the model must fit both device disk and memory, recommends measuring latency and power, and indicates CPU/XNNPACK or NNAPI options depending on the model and device.[3] [4]

| Package property | Required behavior in Nastech |
|---|---|
| Download initiation | Explicit user tap only. Show package size, language/voice scope, storage location, licence, Wi-Fi/mobile-data state, and a cancel action. |
| Storage | App-owned `filesDir` model package directory, never an executable APK or shell payload. |
| Integrity | Versioned manifest with fixed HTTPS source URLs, expected SHA-256 values, file sizes, and package licence. Verify every file before enabling the provider. |
| Resume | Use Android-supported resumable foreground download work with durable progress state; expose retry and remove. |
| Voices | Show only verified IDs shipped in the selected package. Start with a compact English voice set and expand by downloadable voice assets where the model format supports it. |
| Removal | One action removes the package and cache while leaving user’s provider record visible but marked “Download required”. |
| Fallback | System TTS remains selected/available if a local model has not been verified or cannot synthesize a request. |
| Privacy | Synthesis executes on-device after model installation. Nastech does not upload the text for local Kokoro speech. |

Sherpa-ONNX is an alternative integrated runtime: it is Apache-2.0, documents Android and Kotlin support, and publicly lists Android Kokoro TTS variants, including multi-language and int8 variants.[5] [6] The implementation must choose **one** supported runtime and ship its matching tokenizer and model configuration. It must not combine arbitrary model files and native libraries based only on a filename.

## Kokoro provider wiring sequence

1. Add `TTSProviderSetting.KokoroLocal`, including stable provider ID, display name, `voiceId`, speaking rate, selected package ID, and model version. Its serial name preserves existing settings migrations.
2. Add the type to `TTSProviderSetting.Types`, the editor selector, the provider-specific configuration route, and the TTS manager’s two exhaustive dispatch branches.
3. Build `KokoroPackageManager` in the speech or app data layer. It owns package manifests, storage checks, resumable downloads, SHA-256 validation, installation state, removal, and a `StateFlow` observed by Speech Settings.
4. Build one native `KokoroLocalTTSProvider` against the chosen Android runtime. It rejects synthesis with an actionable “Download the local voice package first” error if the selected package is not verified.
5. Generate 24 kHz PCM/WAV through the existing `AudioChunk` / `TtsSynthesizer` / `TtsController` path, so chat reading, the docked reader, Speech Settings test playback, pause/resume, speed, stop, and existing update logic remain one system.
6. Add instrumentation and device tests before selecting Kokoro as a default provider. The current system voice remains the safe default.

## Progressive response and reader behavior

The current reader intentionally performs a timer-based word reveal because the selected TTS provider interface reports playback state and chunk counts, but not spoken character ranges. That is honest but can be improved in two levels.

### Level 1: portable progressive reader

Nastech can split a selected response into sentences or existing `TtsChunk` units. The reader displays the active chunk as it is synthesized and played, fades the completed phrase into the reading history, and animates the next phrase into focus. This works for local, remote, and system voices without claiming exact word alignment.

### Level 2: true spoken-range highlighting

Android’s `UtteranceProgressListener.onRangeStart()` supplies the start and end character indices of text when the system engine is about to speak the range. The official API notes that it is available from API 26 and only if the active engine supplies range timing information. Its callback is suitable for highlighting the range while it is spoken.[7]

Nastech should add a `ReaderProgress` state with `text`, `activeStart`, `activeEnd`, `chunkIndex`, `totalChunks`, and `mode` (`ExactRange`, `PhraseEstimate`, `Waiting`, `Paused`). The System TTS provider can emit exact range state when supported. The local Kokoro provider can only emit exact highlighting if its runtime produces phoneme or sample timestamps; otherwise it must use phrase-aware progress. Remote providers likewise use the portable phrase mode unless their API supplies trustworthy timestamps.

For actual **streaming assistant responses**, Nastech should remain user-controlled: when the reader is active and a message is still being generated, append only completed sentence boundaries to the existing TTS queue with `flush=false`. The visual reader appends the same sentence to its transcript, then marks pending text as “waiting for the next complete sentence.” On generation completion, it enqueues any final punctuation-free tail. The user can pause, stop, or turn off “Read streaming responses” at any time. No speech begins automatically for a new chat response unless the user explicitly enables this setting.

## Black Silence full-app conversion

The prior `GlassAppearance` system is now the configuration layer for Black Silence rather than a second visual identity. The conversion rules are:

| Design rule | Shared implementation |
|---|---|
| AMOLED-black spatial canvas | Root `MeshGradientBackground` with two slow, family-driven blooms and Quiet Motion support. |
| Near-black floating panels | `GlassTheme` resolver and transparent Material surface roles. |
| Fine cool edges and visible text | Shared `glassContentColor()` contrast resolver and soft border token. |
| Rounded, compact control groups | `CardGroup`, provider tiles, chat bubbles, media cards, update notices, sheets, and drawer surfaces. |
| Single controlled accent family | Named Obsidian Neon, Sky Blue, Emerald, Violet, and Sunset choices, plus a custom accent control. |
| Optional animation | Existing motion and sound-reactive preferences; quiet mode freezes decoration without changing content. |

The global layer already reaches default Material components. The remaining explicit conversion pass should replace page-specific opaque cards, generic shapes, and legacy full-black surfaces in settings, agent, provider, storage, browser, extension, and diagnostic pages with their shared Black Silence panels. Functional routes, providers, approvals, chat data, tools, and workspace state must remain unchanged.

## Sources

[1]: https://huggingface.co/onnx-community/Kokoro-82M-v1.0-ONNX "ONNX Community Kokoro-82M v1.0 ONNX model card"
[2]: https://github.com/thewh1teagle/kokoro-onnx "kokoro-onnx"
[3]: https://onnxruntime.ai/docs/tutorials/mobile/ "ONNX Runtime: Develop on mobile"
[4]: https://onnxruntime.ai/docs/install/ "ONNX Runtime: Install on Android"
[5]: https://github.com/k2-fsa/sherpa-onnx "Sherpa-ONNX"
[6]: https://k2-fsa.github.io/sherpa/onnx/tts/apk-engine.html "Sherpa-ONNX Android TTS engine packages"
[7]: https://developer.android.com/reference/android/speech/tts/UtteranceProgressListener "Android UtteranceProgressListener"
