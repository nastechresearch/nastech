# Kokoro Runtime Selection — Nastech

**Prepared:** 14 August 2026
**Decision in progress:** Select a robust Android implementation for an in-app Kokoro voice manager with every supported voice, downloadable packages of up to roughly 700 MB, and an honest CPU/accelerator preference.

## Initial verified findings

| Requirement | Verified result | Implication for Nastech |
| --- | --- | --- |
| Full multi-speaker Kokoro package | Maintained Sherpa-ONNX documentation lists `kokoro-multi-lang-v1_1` with **103 speakers** and `kokoro-multi-lang-v1_0` with 53 speakers. [1] | Use the v1.1 full multilingual bundle as the complete local voice library; the catalogue must be generated from the documented speaker map, not a partial hardcoded list. |
| Alternative raw ONNX distribution | The ONNX Community model provides several graph precisions, including FP32 326 MB, FP16 163 MB, and quantized 92.4 MB; it requires a compatible phonemization/tokenization path as well as voices. [2] | Raw ONNX variants are useful only as optional performance packages. They are not the safest primary Android implementation because model assets alone do not provide a complete, verified Android phonemizer/voice lifecycle. |
| Device acceleration | ONNX Runtime’s Android NNAPI execution provider can target Android CPU, GPU, and neural accelerators; it must be explicitly registered and support varies by model/operator/device. CPU fallback must remain available. [3] | Nastech will provide an actual inference preference: **CPU**, **Auto acceleration (NNAPI)**, and **Accelerator preferred**. It will report fallback rather than claiming all devices have a usable GPU path. |
| Dependency/library trust | The separate `kokoro-onnx` project is MIT-licensed and offers generic ONNX model usage, but its visible Android story is not a maintained Kotlin package manager or mobile phonemizer stack. [4] | Do not base Nastech’s Android local-voice manager on that project. Prefer the maintained Android-compatible Sherpa runtime and its complete model bundle. |

## Preliminary runtime choice

> **Primary runtime:** Sherpa-ONNX Android with the maintained `kokoro-multi-lang-v1_1` package, because it ships the model, 103 speaker embeddings, tokens, lexicons, and eSpeak resources expected by the runtime as one complete downloadable bundle. The target package can remain below the user’s 700 MB ceiling while keeping every supported voice.

The current `KokoroPackageManager` has an older v1.0 complete bundle with 53 voices. It must be replaced by a v1.1 manager with a versioned manifest, safe download/resume, SHA-256 verification, atomic staging, package removal, package-size checks, and all 103 documented voices. The voice setting will select the true speaker name/ID as well as engine precision and acceleration preference.

## References

[1]: https://k2-fsa.github.io/sherpa/onnx/tts/index.html "Sherpa-ONNX TTS models and Kokoro multi-language voice counts"
[2]: https://huggingface.co/onnx-community/Kokoro-82M-ONNX "ONNX Community Kokoro model precisions and sizes"
[3]: https://onnxruntime.ai/docs/execution-providers/NNAPI-ExecutionProvider.html "ONNX Runtime Android NNAPI execution provider"
[4]: https://github.com/thewh1teagle/kokoro-onnx "kokoro-onnx project overview and license"

## Full-package and optimizer findings

The maintained Sherpa documentation publishes two version 1.1 archives: an INT8 package and an unquantized package. It identifies the v1.1 family as a **103-speaker Chinese-and-English** Kokoro bundle. Its supplied Kotlin example uses `OfflineTtsKokoroModelConfig` with the package’s model, `voices.bin`, tokens, eSpeak data, and lexicon files, then selects a speaker by integer `sid`. [5] [6]

> The v1.1 complete bundle is the correct Nastech catalogue target. The package manager will present **all 103 selectable speakers** and preserve the speaker ID mapping in a versioned manifest. It will offer an INT8 package as the default and an unquantized high-fidelity package only where its actual archive size is under the user’s 700 MB ceiling.

The Android Sherpa example verifies that the runtime supports a generation callback, which can be used to feed local audio progressively when the Nastech controller is upgraded from its current collect-to-one-buffer bridge. [6] However, the maintained Android wrapper evidence in this audit does **not** verify a direct Android GPU execution-provider configuration for Sherpa. NNAPI acceleration is real for ONNX Runtime Android but operator support and device delegation vary, so Nastech will not advertise a guaranteed GPU mode. [3]

The hardware setting must therefore work as follows:

| User preference | Actual behavior |
| --- | --- |
| **CPU optimized** | Configure the supported CPU runtime and selected thread count. Always available. |
| **Auto acceleration** | Attempt the supported Android accelerator path only when a compatible runtime/package and device are detected; otherwise report that CPU remains active. |
| **Accelerator preferred** | Request acceleration where a supported execution provider exists, retain CPU kernels for unsupported operators, and display a clear fallback message if the model/device cannot delegate. |

This is an honest optimization interface: it gives the user control, diagnostics, and future extensibility without pretending that every Android GPU can run every Kokoro operator.

[5]: https://k2-fsa.github.io/sherpa/onnx/tts/pretrained_models/kokoro.html "Sherpa-ONNX Kokoro v1.1 package and v1.0 Android configuration"
[6]: https://github.com/k2-fsa/sherpa-onnx/blob/master/kotlin-api-examples/test_tts.kt "Sherpa-ONNX Kotlin Kokoro synthesis and callback example"

## Pinned v1.1 package manifest

The official release supplies two complete v1.1 archives, both under the requested 700 MB ceiling. The app will present both as explicit downloads and will enable a package only after its published SHA-256 matches.

| Package | Purpose | Download size | SHA-256 |
| --- | --- | ---: | --- |
| `kokoro-int8-multi-lang-v1_1.tar.bz2` | Default efficient package | 147,031,220 bytes | `a1e94694776049035c4f2c6529f003aaece993c76aae9a78995831c3c4dcafc6` |
| `kokoro-multi-lang-v1_1.tar.bz2` | Full-precision package | 364,816,464 bytes | `a3f4c73d043860e3fd2e5b06f36795eb81de0fc8e8de6df703245edddd87dbad` |

The v1.1 package has a fixed 24 kHz sample rate and the 103-speaker roster is composed of American female `af_maple` and `af_sol`, British female `bf_vale`, 55 Chinese female `zf_*` voices at IDs 3–57, and 45 Chinese male `zm_*` voices at IDs 58–102. The maintained speaker table provides the authoritative identifier-to-ID map for the complete in-app catalogue. [7] [8]

[7]: https://github.com/k2-fsa/sherpa-onnx/releases/tag/tts-models "Sherpa-ONNX TTS model release assets"
[8]: https://k2-fsa.github.io/sherpa/onnx/tts/all/Chinese-English/kokoro-multi-lang-v1_1.html "Kokoro v1.1 103-speaker map and Android model documentation"

## Verified acceleration selector

The maintained Sherpa provider parser accepts `cpu` and `nnapi` as distinct provider values (alongside non-Android providers such as CUDA and CoreML) and explicitly falls back to CPU for an unsupported value. Nastech can therefore use **CPU** and **Android accelerator (NNAPI)** as real runtime settings. On Android, NNAPI delegates eligible graph segments to the platform-selected accelerator—GPU or NPU where supported—and leaves unsupported operations to CPU/ONNX Runtime. It is not a guarantee of GPU use on every device. [3] [9]

The local settings UI will consequently label the accelerator option **Android accelerator (NNAPI)** rather than simply “GPU.” It will show the actual attempted provider and any fallback/error returned by the runtime. A convenience **Auto** preference will choose NNAPI on Android 9+ and retry CPU safely if model initialization fails; **CPU optimized** will use the configured CPU thread count; and **NNAPI preferred** will request NNAPI directly with a user-visible CPU recovery path.

[9]: https://github.com/k2-fsa/sherpa-onnx/blob/master/sherpa-onnx/csrc/provider.cc "Sherpa-ONNX provider parser supporting CPU and NNAPI"

## Archive-level validation

The full archive was downloaded once from the maintained release and locally verified to match the published SHA-256:

```text
a3f4c73d043860e3fd2e5b06f36795eb81de0fc8e8de6df703245edddd87dbad
```

Its root directory is `kokoro-multi-lang-v1_1/` and contains the complete runtime manifest: `model.onnx`, `voices.bin`, `tokens.txt`, `lexicon-us-en.txt`, `lexicon-zh.txt`, `espeak-ng-data/phondata`, `README.md`, and `LICENSE`. The Nastech package manager will validate these file paths after transactional extraction, preserve the included license, and reject or correct partial or mismatched downloads before TTS can load them.

## Complete voice-catalogue cross-check

The original model repository’s paginated `voices/` directory contains **103 individual voice assets**: 2 American English female voices, 1 British English female voice, 55 Chinese female voices, and 45 Chinese male voices. A secondary rendered speaker table omitted `zf_024` and `zm_016`, although the source model assets include them. The Nastech catalogue therefore includes those entries at their proper contiguous speaker IDs 17 and 65, respectively, and has a build-time integrity assertion requiring exactly 103 IDs from 0 through 102. [10]

[10]: https://huggingface.co/hexgrad/Kokoro-82M-v1.1-zh/tree/main/voices "Original Kokoro v1.1 voice assets"
