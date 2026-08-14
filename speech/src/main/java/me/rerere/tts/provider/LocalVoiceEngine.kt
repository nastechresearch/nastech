package me.rerere.tts.provider

import me.rerere.tts.kokoro.KokoroPackageVariant

/**
 * Nastech-managed local speech choices. Each profile points to one complete Kokoro v1.1 package;
 * every profile exposes the same 103 verified speakers and differs only in model precision.
 */
enum class LocalVoiceEngine(
    val id: String,
    val displayName: String,
    val description: String,
    val estimatedDownloadBytes: Long,
    val estimatedPeakMemoryBytes: Long,
    val modelPackage: KokoroPackageVariant,
) {
    KOKORO_INT8(
        id = "kokoro-int8-v1_1",
        displayName = "Kokoro · Efficient",
        description = "All 103 local voices in the efficient INT8 package.",
        estimatedDownloadBytes = KokoroPackageVariant.INT8.archiveBytes,
        estimatedPeakMemoryBytes = 640L * MEBIBYTE,
        modelPackage = KokoroPackageVariant.INT8,
    ),
    KOKORO_FULL(
        id = "kokoro-full-v1_1",
        displayName = "Kokoro · Full fidelity",
        description = "All 103 local voices in the full-precision package.",
        estimatedDownloadBytes = KokoroPackageVariant.FULL.archiveBytes,
        estimatedPeakMemoryBytes = 900L * MEBIBYTE,
        modelPackage = KokoroPackageVariant.FULL,
    ),
    ;

    companion object {
        fun fromId(id: String?): LocalVoiceEngine = when (id) {
            KOKORO_FULL.id, "kokoro-full" -> KOKORO_FULL
            KOKORO_INT8.id, "kokoro-short-turn", null -> KOKORO_INT8
            else -> KOKORO_INT8
        }

        fun fromPackage(modelPackage: KokoroPackageVariant): LocalVoiceEngine = entries
            .first { it.modelPackage == modelPackage }
    }
}

private const val MEBIBYTE = 1024L * 1024L
