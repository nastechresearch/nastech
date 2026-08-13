# Nastech Black Silence Reference Findings

## Supplied design references

1. `glassizm_widgets.html` establishes the primary ambient composition: near-black canvas, broad purple/cyan/magenta light blooms, translucent rounded widgets, soft white borders, restrained cyan meters, and large high-contrast values.
2. `nastech_glassizm_theme_lab.html` turns the visual style into a system: a stable dark layout with switchable colour families (Obsidian Neon, Sky Blue, Emerald, Violet, Sunset), floating-light surfaces, visible but subtle active states, audio-reactive waveform, slow background drift, and reduced-motion support.
3. `nastech_glassizm_black_silence.html` defines the target application hierarchy: a quiet assistant card, compact voice-activity panel, focused conversation surface, tool/automation controls, configurable visual settings, readable microcopy, and wide rounded near-black panels instead of heavy opaque cards.

## Native implementation rules

- Replace the legacy generic glass treatment with one `Black Silence` material system that keeps opaque black only as the deep base layer. Panels must be translucent, with a low-alpha blue-black surface, soft edge, subtle inner highlight, and visible content contrast.
- Keep the background almost black; any colour should appear as broad, slow ambient bloom and never compete with text or conversation.
- Add five named colour families that preserve layout and accessibility: Obsidian Neon, Sky Blue, Emerald, Violet, Sunset. Each family drives accent, secondary bloom, and selected state rather than hard-coding unrelated screen colours.
- Use consistent large rounded geometry, measured spacing, low-elevation panels, cyan/mint status indicators, condensed waveform treatment, and calm press feedback.
- Respect an explicit Quiet / Reduced Motion preference. Default motion should be understated: slow colour drift, slight panel response, and an audio waveform only while listening or speaking.
- The replacement TTS design should be a docked reader, not a full Voice Call page: it anchors near the bottom of the current screen, tracks reading state, shows the selected heading or message title, provides pause/stop/skip controls, and can promote to a full black focus overlay for long-form reading.
- The long-form focus overlay should darken and optionally blur the surrounding screen, show a quiet waveform and reading title, and reveal text progressively in phrase-sized segments without claiming word-level speech timing unless the active TTS provider exposes reliable callbacks.
- Rich media previews should remain inside the normal chat flow: compact landscape YouTube player, link card with source branding/favicon/open action, and image/audio preview cards. They should not be separate destination pages unless the user explicitly expands them.
- In-app update flow should check GitHub Releases, display release notes and signed APK metadata, download the APK in-app, and hand off to Android's package installer. Android will always show its own confirmation; Nastech must never silently install an APK.

## Reference fidelity risks to avoid

- No black text on translucent black panels.
- No fully opaque stacked card grid that hides the ambient depth.
- No fast particle effects, excess shadows, gradients on every component, or motion that competes with reading.
- No external client branding in optional service cards.
- No isolated tools, skills, or media systems; all additions must open and operate through existing Nastech conversations, approvals, provider configuration, skills, and settings.
