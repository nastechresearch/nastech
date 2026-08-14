# ElevenLabs Speech-to-Speech Architecture Notes

## Verified protocol

Nastech will use the ElevenLabs Agents WebSocket endpoint:

```text
wss://api.elevenlabs.io/v1/convai/conversation?agent_id={agent_id}
```

A private agent should use a short-lived signed conversation URL rather than placing a long-lived ElevenLabs API key in the WebSocket request. The app can obtain that URL through the authenticated `GET /v1/convai/conversation/get-signed-url?agent_id={agent_id}` endpoint with the `xi-api-key` header, then connect to the returned URL. This keeps the configured API key out of WebSocket URLs and logs.

The controller must send a `conversation_initiation_client_data` JSON object after connection, declaring `pcm_16000` as the requested input and output audio format. Microphone PCM16 mono chunks are base64 encoded in messages of the form:

```json
{"user_audio_chunk":"base64EncodedPcm16"}
```

The call expects and produces event JSON. Relevant events are:

| Direction | Event | Nastech handling |
| --- | --- | --- |
| Server to client | `conversation_initiation_metadata` | Verify negotiated PCM16 formats and save the conversation ID. |
| Client to server | `user_audio_chunk` | Stream captured 16 kHz PCM16 microphone data. |
| Server to client | `user_transcript` | Display or retain the user turn transcript without submitting it to Nastech's ordinary chat composer. |
| Server to client | `agent_response` | Surface agent text as live-call activity; the ElevenLabs agent, not the separately selected Nastech text provider, owns this call's model response. |
| Server to client | `audio` | Decode `audio_base_64` and enqueue PCM16 audio in an AudioTrack-based jitter buffer. |
| Server to client | `interruption` | Flush queued/playing agent PCM promptly, allowing barge-in. |
| Server to client | `vad_score` | Update the microphone activity indicator. |
| Server to client | `ping` | Send a matching `pong` after the requested delay. |

## Happy patterns adopted selectively

Happy separates a session coordinator from its platform audio bridge. It records connection state before requesting microphone access, keeps a single active call/session identity, and clears it atomically when a call ends. Its UI state distinguishes `connecting`, `user-speaking`, `agent-speaking`, and `idle`, with VAD debouncing and no duplicate context injection.

Nastech will apply these patterns while preserving its native Android and Compose implementation:

1. A single controller owns the WebSocket, AudioRecord, AudioTrack, coroutine scope, active call ID, and call state.
2. Microphone permission and transient voice audio focus are acquired before connection and are released on every terminal path.
3. Audio runs as continuous PCM16 16 kHz mono in 20 ms chunks; no audio is written to disk.
4. Server audio is queued to avoid overlap. An interruption or locally detected barge-in flushes queued samples and pauses/stops the playback track immediately.
5. The existing compact microphone control starts and ends the call. When the selected provider is ElevenLabs STS, user transcripts do not fill or auto-send the ordinary chat composer.
6. Existing normal TTS remains available for regular text chat, but it is never invoked for audio that the live ElevenLabs agent already returns. This avoids duplicate agent speech.

## Product boundary

An ElevenLabs Conversational AI agent determines the model, voice, tools, and prompt used for its own live call. Nastech therefore asks for both an ElevenLabs API key and an ElevenLabs agent ID. The app’s independently selected text-chat model remains separate from that configured cloud agent during a voice call. The call can show transcripts within Nastech, but it must not masquerade as a response produced by the selected normal chat model.

## References

1. ElevenLabs, [Agent WebSockets API reference](https://elevenlabs.io/docs/eleven-agents/api-reference/eleven-agents/websocket).
2. ElevenLabs, [WebSocket integration guide](https://elevenlabs.io/docs/eleven-agents/libraries/web-sockets).
3. ElevenLabs, [ElevenAgents overview](https://elevenlabs.io/docs/eleven-agents/overview).
4. slopus/happy, [`docs/voice-architecture.md`](https://github.com/slopus/happy/blob/main/docs/voice-architecture.md).
5. slopus/happy, [`RealtimeVoiceSession.tsx`](https://github.com/slopus/happy/blob/main/packages/happy-app/sources/realtime/RealtimeVoiceSession.tsx).
