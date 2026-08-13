# Nastech Black Silence Whole-App Surface Map

## Reference system

The three supplied HTML references define one visual system, not separate screens. Nastech should present an AMOLED-black canvas with two quiet, broad colour blooms; translucent near-black panels; fine cool-white edges; rounded 18–24 dp geometry; high-contrast white copy; a single family-driven cyan, mint, violet, or sunset accent; and motion that can be reduced without reducing functionality.

## Global conversion layers

| Layer | Shared implementation point | Scope of effect |
|---|---|---|
| Canvas and blooms | `RouteActivity.kt` and `MeshGradientBackground.kt` | Every destination, popup stack, navigation root, dialog background, and drawer backdrop. |
| Material fallback surfaces | `Theme.kt` | Default Compose cards, dialogs, menus, sheets, navigation bars, and uncustomized layouts. |
| Configurable glass materials | `GlassAppearance.kt` and `GlassTheme.kt` | Global master controls, five colour families, per-surface overrides, contrast-safe text, border/highlight/opacity controls. |
| Shared bars, cards, and lists | `Color.kt` | Existing top bars, list rows, and cards that already use `CustomColors`. |
| Chat content | `ChatMessage.kt`, `ChatRichPreview.kt`, `ChainOfThought.kt`, `ChatInput.kt` | User and assistant bubbles, reasoning activity, media cards, composer, approvals, suggested actions. |
| Reading and voice | `ScreenReader.kt`, `TTSController.kt`, Speech settings, message actions | Docked reader and optional full-screen quiet focus state; no standalone orb page. |
| Navigation and control centres | `ChatDrawer.kt`, `Setting*.kt`, `AgentBridgePage.kt`, onboarding and provider pages | Drawers, control grids, settings rows, sheets, popups, progress states, provider and tool cards. |

## Page and component conversion order

| Pass | Areas | Required Black Silence treatment |
|---|---|---|
| 1 | Root, chat, drawer, reader, media preview, update notice | Canvas bloom, translucent panels, clear selected state, compact wave/meter, readable text. |
| 2 | Settings, Appearance, Provider, Telegram, Web Access, Storage, Speech, Agent bridge | Large rounded panels, compact category chips, muted secondary labels, outlined popup editors, no opaque stacked cards. |
| 3 | Assistants, model pickers, extensions, skills, workspaces, history, search, browser, backup | Shared surface fallback plus explicit panels for the most frequently used cards and sheets. |
| 4 | Error, permission, tool approval, import/export, developer, diagnostics, onboarding | Calm severity treatments on the same near-black material; maintain clarity for approvals and destructive operations. |

## Fidelity rules

The root background must remain nearly black. Any colour appears only as a diffused family-driven bloom or an active state. Panels must not be full opaque black. Every foreground is resolved against its actual translucent surface. Motion stays slow and optional; no decorative particles, persistent pulse, or aggressive shadows are introduced. Standard screens and optional service views use Nastech labels only, while third-party marks remain limited to explicit integration cards.

The implementation must preserve the current settings, providers, approvals, conversation state, external connections, skills, workspace data, and navigation destinations. A visual conversion never replaces a functional route with an isolated duplicate.
