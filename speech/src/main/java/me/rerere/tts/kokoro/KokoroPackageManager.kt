package me.rerere.tts.kokoro

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** UI-facing state for the single, user-managed local Kokoro model package. */
sealed interface KokoroPackageState {
    data object NotDownloaded : KokoroPackageState
    data class Downloading(val downloadedBytes: Long, val totalBytes: Long) : KokoroPackageState
    data object Verifying : KokoroPackageState
    data class Ready(val directory: File, val installedBytes: Long) : KokoroPackageState
    data class Error(val message: String) : KokoroPackageState
}

data class KokoroVoice(
    val id: String,
    val speakerId: Int,
    val label: String,
)

/**
 * The full v1.0 multi-language bundle is used instead of a raw ONNX file. It contains the model,
 * all 53 speaker embeddings, token list, lexicons, and eSpeak data expected by Sherpa-ONNX.
 *
 * The archive digest was calculated from the maintained release asset on 2026-08-14. A package is
 * never marked ready unless this digest matches and the required internal model files are present.
 */
object KokoroModelPackage {
    const val ID = "kokoro-multi-lang-v1_0"
    const val VERSION = "1.0"
    const val LICENSE = "Apache-2.0"
    const val ARCHIVE_BYTES = 349_418_188L
    const val ARCHIVE_SHA256 = "c133d26353d776da730870dac7da07dbfc9a5e3bc80cc5e8e83ab6e823be7046"
    const val ARCHIVE_URL =
        "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/kokoro-multi-lang-v1_0.tar.bz2"

    val voices: List<KokoroVoice> = listOf(
        KokoroVoice("af_alloy", 0, "Alloy · American English"),
        KokoroVoice("af_aoede", 1, "Aoede · American English"),
        KokoroVoice("af_bella", 2, "Bella · American English"),
        KokoroVoice("af_heart", 3, "Heart · American English"),
        KokoroVoice("af_jessica", 4, "Jessica · American English"),
        KokoroVoice("af_kore", 5, "Kore · American English"),
        KokoroVoice("af_nicole", 6, "Nicole · American English"),
        KokoroVoice("af_nova", 7, "Nova · American English"),
        KokoroVoice("af_river", 8, "River · American English"),
        KokoroVoice("af_sarah", 9, "Sarah · American English"),
        KokoroVoice("af_sky", 10, "Sky · American English"),
        KokoroVoice("am_adam", 11, "Adam · American English"),
        KokoroVoice("am_echo", 12, "Echo · American English"),
        KokoroVoice("am_eric", 13, "Eric · American English"),
        KokoroVoice("am_fenrir", 14, "Fenrir · American English"),
        KokoroVoice("am_liam", 15, "Liam · American English"),
        KokoroVoice("am_michael", 16, "Michael · American English"),
        KokoroVoice("am_onyx", 17, "Onyx · American English"),
        KokoroVoice("am_puck", 18, "Puck · American English"),
        KokoroVoice("am_santa", 19, "Santa · American English"),
        KokoroVoice("bf_alice", 20, "Alice · British English"),
        KokoroVoice("bf_emma", 21, "Emma · British English"),
        KokoroVoice("bf_isabella", 22, "Isabella · British English"),
        KokoroVoice("bf_lily", 23, "Lily · British English"),
        KokoroVoice("bm_daniel", 24, "Daniel · British English"),
        KokoroVoice("bm_fable", 25, "Fable · British English"),
        KokoroVoice("bm_george", 26, "George · British English"),
        KokoroVoice("bm_lewis", 27, "Lewis · British English"),
        KokoroVoice("ef_dora", 28, "Dora · Spanish"),
        KokoroVoice("em_alex", 29, "Alex · Spanish"),
        KokoroVoice("ff_siwis", 30, "Siwis · French"),
        KokoroVoice("hf_alpha", 31, "Alpha · Hindi"),
        KokoroVoice("hf_beta", 32, "Beta · Hindi"),
        KokoroVoice("hm_omega", 33, "Omega · Hindi"),
        KokoroVoice("hm_psi", 34, "Psi · Hindi"),
        KokoroVoice("if_sara", 35, "Sara · Italian"),
        KokoroVoice("im_nicola", 36, "Nicola · Italian"),
        KokoroVoice("jf_alpha", 37, "Alpha · Japanese"),
        KokoroVoice("jf_gongitsune", 38, "Gongitsune · Japanese"),
        KokoroVoice("jf_nezumi", 39, "Nezumi · Japanese"),
        KokoroVoice("jf_tebukuro", 40, "Tebukuro · Japanese"),
        KokoroVoice("jm_kumo", 41, "Kumo · Japanese"),
        KokoroVoice("pf_dora", 42, "Dora · Portuguese"),
        KokoroVoice("pm_alex", 43, "Alex · Portuguese"),
        KokoroVoice("pm_santa", 44, "Santa · Portuguese"),
        KokoroVoice("zf_xiaobei", 45, "Xiaobei · Mandarin Chinese"),
        KokoroVoice("zf_xiaoni", 46, "Xiaoni · Mandarin Chinese"),
        KokoroVoice("zf_xiaoxiao", 47, "Xiaoxiao · Mandarin Chinese"),
        KokoroVoice("zf_xiaoyi", 48, "Xiaoyi · Mandarin Chinese"),
        KokoroVoice("zm_yunjian", 49, "Yunjian · Mandarin Chinese"),
        KokoroVoice("zm_yunxi", 50, "Yunxi · Mandarin Chinese"),
        KokoroVoice("zm_yunxia", 51, "Yunxia · Mandarin Chinese"),
        KokoroVoice("zm_yunyang", 52, "Yunyang · Mandarin Chinese"),
    )

    fun speakerId(voiceId: String): Int = voices.firstOrNull { it.id == voiceId }?.speakerId
        ?: voices.first { it.id == "af_heart" }.speakerId
}

/**
 * Owns the on-device lifecycle for the pinned, downloadable Kokoro package. There is no automatic
 * mobile-data download and no executable payload: only a verified model archive is expanded under
 * [Context.filesDir]. The caller must invoke [download] from an explicit user action.
 */
class KokoroPackageManager(context: Context) {
    private val packageRoot = File(context.filesDir, "kokoro-models")
    private val packageDir = File(packageRoot, KokoroModelPackage.ID)
    private val archiveFile = File(packageRoot, "${KokoroModelPackage.ID}.tar.bz2")
    private val partialArchiveFile = File(packageRoot, "${KokoroModelPackage.ID}.tar.bz2.partial")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var downloadJob: Job? = null

    private val _state = MutableStateFlow<KokoroPackageState>(scanInstalledPackage())
    val state: StateFlow<KokoroPackageState> = _state.asStateFlow()

    fun download() {
        if (downloadJob?.isActive == true) return
        downloadJob = scope.launch {
            runCatching { downloadAndInstall() }
                .onFailure { error ->
                    _state.value = KokoroPackageState.Error(
                        error.message ?: "The local voice package could not be installed",
                    )
                }
        }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
        _state.value = scanInstalledPackage()
    }

    fun remove() {
        cancelDownload()
        packageDir.deleteRecursively()
        archiveFile.delete()
        partialArchiveFile.delete()
        _state.value = KokoroPackageState.NotDownloaded
    }

    fun readyDirectory(): File? = (state.value as? KokoroPackageState.Ready)?.directory
        ?.takeIf(::isVerifiedPackage)

    fun close() {
        cancelDownload()
        scope.cancel()
    }

    private fun downloadAndInstall() {
        packageRoot.mkdirs()
        val existing = partialArchiveFile.takeIf { it.isFile }?.length() ?: 0L
        val connection = (URL(KokoroModelPackage.ARCHIVE_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("Accept", "application/octet-stream")
            setRequestProperty("User-Agent", "Nastech-Kokoro-Downloader")
            if (existing > 0L) setRequestProperty("Range", "bytes=$existing-")
        }
        try {
            val response = connection.responseCode
            if (response !in listOf(HttpURLConnection.HTTP_OK, HttpURLConnection.HTTP_PARTIAL)) {
                error("Voice package download failed (HTTP $response)")
            }
            val resume = response == HttpURLConnection.HTTP_PARTIAL && existing > 0L
            if (!resume) partialArchiveFile.delete()
            val writtenBefore = if (resume) existing else 0L
            val contentBytes = connection.contentLengthLong.takeIf { it > 0L } ?: 0L
            val totalBytes = if (contentBytes > 0L) writtenBefore + contentBytes else KokoroModelPackage.ARCHIVE_BYTES
            connection.inputStream.use { input ->
                FileOutputStream(partialArchiveFile, resume).use { output ->
                    val buffer = ByteArray(BUFFER_BYTES)
                    var downloaded = writtenBefore
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        downloaded += count
                        _state.value = KokoroPackageState.Downloading(downloaded, totalBytes)
                    }
                    output.fd.sync()
                }
            }
        } finally {
            connection.disconnect()
        }

        if (partialArchiveFile.length() != KokoroModelPackage.ARCHIVE_BYTES) {
            error("The downloaded voice package size did not match the expected archive")
        }
        _state.value = KokoroPackageState.Verifying
        if (!sha256(partialArchiveFile).equals(KokoroModelPackage.ARCHIVE_SHA256, ignoreCase = true)) {
            partialArchiveFile.delete()
            error("The downloaded voice package failed integrity verification")
        }
        if (archiveFile.exists()) archiveFile.delete()
        if (!partialArchiveFile.renameTo(archiveFile)) {
            partialArchiveFile.copyTo(archiveFile, overwrite = true)
            partialArchiveFile.delete()
        }

        val staging = File(packageRoot, ".${KokoroModelPackage.ID}-staging")
        staging.deleteRecursively()
        staging.mkdirs()
        try {
            extractSafely(archiveFile, staging)
            val extracted = File(staging, KokoroModelPackage.ID)
            if (!isModelLayoutComplete(extracted)) {
                error("The verified voice package is missing required speech assets")
            }
            packageDir.deleteRecursively()
            if (!extracted.renameTo(packageDir)) {
                extracted.copyRecursively(packageDir, overwrite = true)
                extracted.deleteRecursively()
            }
            File(packageDir, READY_MARKER).writeText(KokoroModelPackage.ARCHIVE_SHA256)
            archiveFile.delete()
            _state.value = KokoroPackageState.Ready(packageDir, installedSize(packageDir))
        } finally {
            staging.deleteRecursively()
        }
    }

    private fun extractSafely(archive: File, destination: File) {
        val destinationPath = destination.canonicalFile
        var unpackedBytes = 0L
        BZip2CompressorInputStream(FileInputStream(archive)).use { compressed ->
            TarArchiveInputStream(compressed).use { tar ->
                while (true) {
                    val entry = tar.nextTarEntry ?: break
                    if (entry.isSymbolicLink || entry.isLink) continue
                    val target = File(destination, entry.name).canonicalFile
                    if (!target.path.startsWith(destinationPath.path + File.separator)) {
                        error("Unsafe file path in voice package")
                    }
                    if (entry.isDirectory) {
                        target.mkdirs()
                        continue
                    }
                    if (entry.size < 0 || entry.size > MAX_SINGLE_ENTRY_BYTES) {
                        error("Invalid file size in voice package")
                    }
                    unpackedBytes += entry.size
                    if (unpackedBytes > MAX_UNPACKED_BYTES) error("Voice package exceeds safe extraction size")
                    target.parentFile?.mkdirs()
                    FileOutputStream(target).use { output ->
                        val buffer = ByteArray(BUFFER_BYTES)
                        var remaining = entry.size
                        while (remaining > 0) {
                            val count = tar.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                            if (count < 0) error("Unexpected end of voice package")
                            output.write(buffer, 0, count)
                            remaining -= count
                        }
                    }
                }
            }
        }
    }

    private fun scanInstalledPackage(): KokoroPackageState {
        return if (isVerifiedPackage(packageDir)) {
            KokoroPackageState.Ready(packageDir, installedSize(packageDir))
        } else {
            KokoroPackageState.NotDownloaded
        }
    }

    private fun isVerifiedPackage(directory: File): Boolean {
        val marker = File(directory, READY_MARKER)
        return marker.isFile &&
            marker.readText().trim().equals(KokoroModelPackage.ARCHIVE_SHA256, ignoreCase = true) &&
            isModelLayoutComplete(directory)
    }

    private fun isModelLayoutComplete(directory: File): Boolean = REQUIRED_MODEL_FILES.all { relativePath ->
        File(directory, relativePath).isFile
    }

    private fun installedSize(directory: File): Long = directory.walkTopDown()
        .filter { it.isFile }
        .sumOf { it.length() }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(BUFFER_BYTES)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }

    private companion object {
        const val READY_MARKER = ".nastech-kokoro-ready"
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 45_000
        const val BUFFER_BYTES = 64 * 1024
        const val MAX_SINGLE_ENTRY_BYTES = 512L * 1024L * 1024L
        const val MAX_UNPACKED_BYTES = 1_200L * 1024L * 1024L
        val REQUIRED_MODEL_FILES = listOf(
            "model.onnx",
            "voices.bin",
            "tokens.txt",
            "lexicon-us-en.txt",
            "lexicon-zh.txt",
            "espeak-ng-data/phondata",
        )
    }
}
