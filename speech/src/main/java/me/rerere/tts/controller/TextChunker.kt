package me.rerere.tts.controller

/**
 * Creates low-latency, speech-friendly batches for the progressive reader.
 *
 * A short response is kept intact so it can begin speaking immediately. Longer material starts
 * with a deliberately smaller first batch, then continues with larger sentence-aware batches to
 * keep synthesis and playback moving without flooding a local model with many tiny requests.
 */
class TextChunker(
    private val firstChunkTarget: Int = 88,
    private val steadyChunkTarget: Int = 210,
    private val maximumChunkLength: Int = 280,
) {
    init {
        require(firstChunkTarget in 24..maximumChunkLength)
        require(steadyChunkTarget in firstChunkTarget..maximumChunkLength)
    }

    fun split(text: String): List<TtsChunk> {
        val normalized = normalize(text)
        if (normalized.isBlank()) return emptyList()
        if (normalized.length <= firstChunkTarget) {
            return listOf(TtsChunk(index = 0, text = normalized))
        }

        val chunks = mutableListOf<String>()
        val pending = StringBuilder()
        sentenceUnits(normalized).forEach { unit ->
            appendUnit(unit, pending, chunks)
        }
        flush(pending, chunks)

        return chunks.mapIndexed { index, value ->
            TtsChunk(index = index, text = value)
        }
    }

    private fun appendUnit(unit: String, pending: StringBuilder, chunks: MutableList<String>) {
        var remaining = unit.trim()
        while (remaining.isNotEmpty()) {
            val target = if (chunks.isEmpty()) firstChunkTarget else steadyChunkTarget
            val separatorLength = if (pending.isEmpty()) 0 else 1
            val available = target - pending.length - separatorLength

            if (remaining.length <= available.coerceAtLeast(0)) {
                if (pending.isNotEmpty()) pending.append(' ')
                pending.append(remaining)
                return
            }

            if (pending.isNotEmpty()) {
                flush(pending, chunks)
                continue
            }

            val splitAt = preferredSplit(remaining, target)
            chunks += remaining.take(splitAt).trim()
            remaining = remaining.drop(splitAt).trimStart()
        }
    }

    private fun flush(pending: StringBuilder, chunks: MutableList<String>) {
        pending.toString().trim().takeIf { it.isNotEmpty() }?.let(chunks::add)
        pending.clear()
    }

    private fun sentenceUnits(text: String): List<String> = text
        .split(SENTENCE_BOUNDARY)
        .asSequence()
        .map(String::trim)
        .filter(String::isNotEmpty)
        .toList()
        .ifEmpty { listOf(text) }

    private fun preferredSplit(text: String, preferredLength: Int): Int {
        val boundedLength = preferredLength.coerceIn(1, maximumChunkLength).coerceAtMost(text.length)
        if (boundedLength == text.length) return text.length

        val searchStart = (boundedLength - BREAK_SEARCH_RADIUS).coerceAtLeast(1)
        val punctuation = text.lastIndexOfAny(PREFERRED_BREAKS, startIndex = boundedLength - 1)
        if (punctuation >= searchStart) return punctuation + 1

        val whitespace = text.lastIndexOf(' ', startIndex = boundedLength - 1)
        if (whitespace >= searchStart) return whitespace + 1

        return boundedLength
    }

    private fun normalize(text: String): String = text
        .replace("\r\n", "\n")
        .replace(WHITESPACE, " ")
        .trim()

    private companion object {
        val WHITESPACE = Regex("\\s+")
        val SENTENCE_BOUNDARY = Regex("(?<=[.!?…。！？])\\s+|\\n+")
        val PREFERRED_BREAKS = charArrayOf(',', ';', ':', '，', '、', '；', '：')
        const val BREAK_SEARCH_RADIUS = 48
    }
}

data class TtsChunk(
    val id: java.util.UUID = java.util.UUID.randomUUID(),
    val index: Int,
    val text: String,
)
