# Rich-response and Termux execution research

## Android WebView artifact-preview safeguards

Android’s WebView guidance treats generated or user-controlled HTML as untrusted content. The dedicated in-chat artifact preview must therefore use a restricted WebView profile with no JavaScript interfaces, no file/content access, and no universal file access. Local preview assets should use the existing HTTPS-style virtual origin rather than `file://`; external navigation must be blocked or handed off to the device browser after user action. JavaScript should be enabled only for the self-contained animation code shipped in the generated preview document, not remote scripts.

The current shared WebView enables JavaScript and content access, so it is unsuitable as-is for raw assistant HTML. A dedicated `SafeArtifactWebView` profile should explicitly set `allowFileAccess = false`, `allowContentAccess = false`, `allowFileAccessFromFileURLs = false`, `allowUniversalAccessFromFileURLs = false`, disable geolocation/media/file chooser behavior, and attach no native bridge. The existing `https://nastech.local` cache/base origin is appropriate for the locally controlled preview shell.

Sources:

1. [Android Developers — WebViews: Unsafe File Inclusion](https://developer.android.com/privacy-and-security/risks/webview-unsafe-file-inclusion), updated 2026-07-31.
2. [Android Developers — WebView Native Bridges](https://developer.android.com/privacy-and-security/risks/insecure-webview-native-bridges), updated 2024-10-15.

## Termux background-command constraints

Termux’s RUN_COMMAND integration requires the caller’s `com.termux.permission.RUN_COMMAND` permission and the user-enabled `allow-external-apps=true` setting. A `PendingIntent` result callback can return separate stdout, stderr, and exit code for background commands; Termux documents that large response payloads are truncated and that background work may be killed by device battery controls. The app should preserve its existing approval gate, identify background tasks with explicit labels, show a task status row, and provide user-visible cancellation instructions rather than silently writing or launching long-running projects.

Termux’s security guidance highlights that this capability can access private Termux files. Nastech must not grant raw generated HTML any bridge to tool execution, and project writes must require explicit confirmation through the existing approval framework.

Source:

3. [Termux RUN_COMMAND Intent wiki](https://github.com/termux/termux-app/wiki/RUN_COMMAND-Intent).
4. [Termux Apps Vulnerability Disclosures](https://termux.dev/en/posts/security/2022/02/15/termux-apps-vulnerability-disclosures.html).

## Feature direction from current agent workflows

High-value integrated features to prioritize are interactive artifacts that render inside chat, inspectable code/HTML previews, project-scoped save confirmation, tool-call visibility and approval, durable background-task state, and model/tool catalog management. These should be added as parts of the existing chat, settings, and tool workflow rather than standalone pages.

Reference discovery:

5. [VS Code — Build with agents](https://code.visualstudio.com/docs/agents/overview).
6. [Google Developers Blog — Build with Google Antigravity](https://developers.googleblog.com/build-with-google-antigravity-our-new-agentic-development-platform/).

## Current agent-product patterns verified

Current agent platforms consistently treat planning, scoped workspace context, approvals, task visibility, artifacts, and review/revert controls as core features. VS Code’s current agent documentation describes plans, workspace-scoped operations, user-controlled permissions, tools, MCP connections, skills, and review/revert as integrated flows. Google’s Antigravity announcement similarly highlights asynchronous long-running tasks with a manager surface and reviewable artifacts rather than raw logs.

For Nastech, the corresponding practical priorities are:

- Present generated HTML, Markdown, diagrams, and project output as readable, inspectable artifacts in chat.
- Keep code and command execution transparent through existing tool approval cards, concise status, and final artifacts.
- Treat storage as an explicit user decision: temporary in-chat previews remain cached; writing a project file or exporting a document requires confirmation.
- Keep Termux work user-visible with an execution label, start/result state, and a clear way to open the visible terminal for manual intervention; do not expose it to WebView content.
- Use existing skills, MCP, provider, assistant, and tool settings as the capability catalogue rather than add duplicate management screens.

References:

7. [VS Code — Build with agents](https://code.visualstudio.com/docs/agents/overview), accessed 2026-08-14.
8. [Google Developers Blog — Build with Google Antigravity](https://developers.googleblog.com/build-with-google-antigravity-our-new-agentic-development-platform/), 2025-11-20.


---

## Nastech Hybrid Structured-Response Architecture (Research & Implementation Roadmap)

### Executive Summary

To make **Nastech** (`io.github.nastechresearch.nastech`) exceptionally strong, secure, responsive, and visually immersive, this research brief establishes a production-grade architectural blueprint. Moving away from raw, unconstrained AI-generated HTML or arbitrary client-side execution, Nastech adopts a **hybrid schema-first renderer**. 

Under this model, the AI model governs a declarative JSON protocol containing typed content blocks and theme directives, while the native Android application (built with Jetpack Compose and Material 3) enforces security filtering, schema validation, state interpolation, and butter-smooth UI animations. Genuinely custom visualizations are rendered via a strictly isolated, restricted `WebView` sandbox, insulating the host application from injection and privilege escalation.

### 1. Core Architectural Paradigm: Schema-First Control

Allowing an LLM to generate raw UI code or execute arbitrary JavaScript within a mobile context introduces severe remote code execution (RCE) and data-exfiltration risks [3]. In the Nastech architecture, the model acts as a **director**, not an engineer. It emits a structured JSON payload that conforms to a strict schema.

```
AI Model (Cloud LLM)
       │  (Emits JSON Protocol: Blocks + Theme)
       ▼
JSON Schema Validator (Kotlinx.serialization)
       │  (Rejects unknown components or malicious markup)
       ▼
Secure Filtering & Component Registry
       │  (Maps verified blocks to Jetpack Compose or Sandboxed WebView)
       ▼
Material 3 Theme Engine & Compose Animator (60fps UI)
```

### 2. The Hybrid Renderer Split: Compose vs. WebView

- **Native Jetpack Compose (90% of Responses)**: Standard conversation, Markdown, tool steps, progress meters, timelines, and cards are rendered natively via Jetpack Compose [2]. Provides fluid 60fps animations, zero layout jitter, and instant scrolling.
- **Isolated WebView (10% of Responses)**: Custom interactive widgets, canvas graphics, or custom HTML layouts are routed through a dedicated, locked-down `WebView` wrapper with `javaScriptEnabled = true` only for the local artifact, zero JavaScript bridge (`addJavascriptInterface`), and disabled file/content access.

### 3. Dynamic Theming & Smooth State Interpolation

Nastech introduces an **AI Theme Engine** to smoothly transition between visual moods (e.g., technical space exploration vs. warm culinary guides) using Material 3 `ColorScheme` binding, spring physics, and animated color transitions (`animateColorAsState`).

### 4. Security Audit & Threat Mitigation

- **XSS Prevention**: Strict Markdown parser sanitization; WebView runs in sandbox with restricted settings.
- **RCE Prevention**: Strict JSON Schema validation. Unknown block types are dropped instantly. No dynamic code evaluation (`eval()`) is permitted.
- **Bridge Isolation**: `addJavascriptInterface` is never called for dynamic model outputs.

### References

[1] Google Android Developers. *Security Tips for WebView*. https://developer.android.com/privacy-and-security/security-tips#WebView  
[2] Google Android Developers. *Jetpack Compose Material 3 Theming*. https://developer.android.com/jetpack/compose/designsystems/material3  
[3] OWASP. *Cross-Site Scripting (XSS) Prevention Cheat Sheet*. https://cheatsheetseries.owasp.org/cheatsheets/Cross_Site_Scripting_Prevention_Cheat_Sheet.html  
