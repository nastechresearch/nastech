# Kokoro TTS and OpenCode Integration Findings

## Kokoro TTS

The reviewed Android reference implementation, [puff-dayo/Kokoro-82M-Android](https://github.com/puff-dayo/Kokoro-82M-Android), describes a minimal on-device Android demo for an **int8-quantized Kokoro-82M model**. It links to the upstream Kokoro model and the `kokoro-onnx` runtime implementation. This supports an Android approach based on locally packaged or user-downloaded ONNX model, voice assets, a Kotlin inference wrapper, and Android audio playback. The reference demo itself is GPL-3.0, so it should be treated as a design reference; Nastech should use compatible upstream runtime/model assets and original integration code rather than copy demo code without a licensing review.

Sources: [Kokoro Android reference](https://github.com/puff-dayo/Kokoro-82M-Android), [Kokoro upstream](https://github.com/hexgrad/kokoro), [kokoro-onnx](https://github.com/thewh1teagle/kokoro-onnx).

## OpenCode

The official [OpenCode server documentation](https://opencode.ai/docs/server/) describes `opencode serve` as a headless HTTP server exposing an OpenAPI 3.1 endpoint. A client can connect to a running server, and the server supports HTTP basic authentication through `OPENCODE_SERVER_PASSWORD`, with the username defaulting to `opencode` or overridden by `OPENCODE_SERVER_USERNAME`. The service normally binds to a local host and port, which makes the safe Nastech integration path a configurable, opt-in remote/local OpenCode server provider rather than embedding the OpenCode runtime into the Android APK.

Sources: [OpenCode server documentation](https://opencode.ai/docs/server/), [OpenCode SDK documentation](https://opencode.ai/docs/sdk/).

## Initial implementation direction

Nastech should add OpenCode as a configurable OpenAI-compatible or dedicated HTTP provider with user-supplied server URL and basic-auth credentials stored in the app's existing secure configuration path. Kokoro should be added as an opt-in provider with a clear model-installation requirement, avoiding a multi-hundred-megabyte default application package.

## Verified API contracts

The reviewed [Kokoro-FastAPI project](https://github.com/remsky/Kokoro-FastAPI) exposes an OpenAI-compatible service at `http://localhost:8880/v1`. Its speech route is `POST /v1/audio/speech`, with `model`, `voice`, `input`, and `response_format` fields. It supports MP3, WAV, Opus, FLAC, AAC, and PCM output. This lets Nastech use a dedicated Kokoro provider that follows the project’s existing OpenAI-style TTS request pattern while keeping model execution self-hosted.

The reviewed [OpenCode session prompt reference](https://opencode.ai/v2/docs/api/session/v2-session-prompt) defines `POST /api/session/{sessionID}/prompt`. The prompt payload includes `text` and can include `files`, `agents`, `skills`, `metadata`, `delivery`, and `resume`. The companion server documentation defines basic-auth protection via `OPENCODE_SERVER_PASSWORD` and an optional `OPENCODE_SERVER_USERNAME`. Therefore, Nastech should use a dedicated OpenCode client setting with server URL, username, password, project/session selection, and an explicit warning that the configured server may execute coding-agent tools on its own host.

## Recommended first increment

Implement Kokoro first as a self-hosted OpenAI-compatible TTS provider, defaulting to `http://localhost:8880/v1`, model `kokoro`, voice `af_bella`, and MP3 output. Implement OpenCode as an opt-in assistant-provider connection that verifies server health, creates or reuses a session, and submits prompts through the documented session route. Do not ship or start an OpenCode runtime in the Android APK; it is intended to connect to an OpenCode service owned and configured by the user.
