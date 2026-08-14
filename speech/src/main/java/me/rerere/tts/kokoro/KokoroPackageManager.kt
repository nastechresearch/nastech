package me.rerere.tts.kokoro

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
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

/** The complete, downloadable package choices supplied by the maintained Kokoro v1.1 release. */
enum class KokoroPackageVariant(
    val id: String,
    val displayName: String,
    val description: String,
    val directoryName: String,
    val modelFileName: String,
    val archiveUrl: String,
    val archiveBytes: Long,
    val archiveSha256: String,
) {
    INT8(
        id = "kokoro-int8-v1_1",
        displayName = "Efficient · INT8",
        description = "147 MB download. Optimized for lower storage use and faster mobile inference.",
        directoryName = "kokoro-int8-multi-lang-v1_1",
        modelFileName = "model.int8.onnx",
        archiveUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/kokoro-int8-multi-lang-v1_1.tar.bz2",
        archiveBytes = 147_031_220L,
        archiveSha256 = "a1e94694776049035c4f2c6529f003aaece993c76aae9a78995831c3c4dcafc6",
    ),
    FULL(
        id = "kokoro-full-v1_1",
        displayName = "Full fidelity",
        description = "365 MB download. Complete full-precision model for maximum on-device quality.",
        directoryName = "kokoro-multi-lang-v1_1",
        modelFileName = "model.onnx",
        archiveUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/kokoro-multi-lang-v1_1.tar.bz2",
        archiveBytes = 364_816_464L,
        archiveSha256 = "a3f4c73d043860e3fd2e5b06f36795eb81de0fc8e8de6df703245edddd87dbad",
    ),
    ;

    companion object {
        fun fromId(id: String?): KokoroPackageVariant = entries.firstOrNull { it.id == id }
            ?: INT8
    }
}

/** UI-facing state for one user-selected local Kokoro package. */
sealed interface KokoroPackageState {
    val modelPackage: KokoroPackageVariant

    data class NotDownloaded(
        override val modelPackage: KokoroPackageVariant,
    ) : KokoroPackageState

    data class Downloading(
        override val modelPackage: KokoroPackageVariant,
        val downloadedBytes: Long,
        val totalBytes: Long,
    ) : KokoroPackageState

    data class Verifying(
        override val modelPackage: KokoroPackageVariant,
    ) : KokoroPackageState

    data class Ready(
        override val modelPackage: KokoroPackageVariant,
        val directory: File,
        val installedBytes: Long,
    ) : KokoroPackageState

    data class Error(
        override val modelPackage: KokoroPackageVariant,
        val message: String,
    ) : KokoroPackageState
}

data class KokoroVoice(
    val id: String,
    val speakerId: Int,
    val label: String,
    val language: String,
    val voiceGroup: String,
)

/**
 * Versioned metadata for the fully local Kokoro v1.1 voice library. Both package variants contain
 * the same 103 speaker embeddings and only differ in model precision/size.
 */
object KokoroModelPackage {
    const val VERSION = "1.1"
    const val LICENSE = "Apache-2.0"
    const val SAMPLE_RATE_HZ = 24_000
    const val DEFAULT_VOICE_ID = "af_maple"

    /** Kept for compatibility with code that needs a single default package identifier. */
    const val ID = "kokoro-int8-v1_1"

    val variants: List<KokoroPackageVariant> = KokoroPackageVariant.entries

    val voices: List<KokoroVoice> = buildList {
        add(KokoroVoice("af_maple", 0, "Maple · American English", "English", "English voices"))
        add(KokoroVoice("af_sol", 1, "Sol · American English", "English", "English voices"))
        add(KokoroVoice("bf_vale", 2, "Vale · British English", "English", "English voices"))

        val chineseFemale = listOf(
            "001", "002", "003", "004", "005", "006", "007", "008", "017", "018", "019",
            "021", "022", "023", "024", "026", "027", "028", "032", "036", "038", "039", "040",
            "042", "043", "044", "046", "047", "048", "049", "051", "059", "060", "067",
            "070", "071", "072", "073", "074", "075", "076", "077", "078", "079", "083",
            "084", "085", "086", "087", "088", "090", "092", "093", "094", "099",
        )
        chineseFemale.forEachIndexed { index, suffix ->
            add(
                KokoroVoice(
                    id = "zf_$suffix",
                    speakerId = index + 3,
                    label = "zf_$suffix · Mandarin Chinese · Female",
                    language = "Mandarin Chinese",
                    voiceGroup = "Mandarin Chinese · Female",
                ),
            )
        }

        val chineseMale = listOf(
            "009", "010", "011", "012", "013", "014", "015", "016", "020", "025", "029", "030",
            "031", "033", "034", "035", "037", "041", "045", "050", "052", "053", "054",
            "055", "056", "057", "058", "061", "062", "063", "064", "065", "066", "068",
            "069", "080", "081", "082", "089", "091", "095", "096", "097", "098", "100",
        )
        chineseMale.forEachIndexed { index, suffix ->
            add(
                KokoroVoice(
                    id = "zm_$suffix",
                    speakerId = index + 58,
                    label = "zm_$suffix · Mandarin Chinese · Male",
                    language = "Mandarin Chinese",
                    voiceGroup = "Mandarin Chinese · Male",
                ),
            )
        }
    }

    init {
        check(voices.size == 103) { "Kokoro v1.1 must expose all 103 speakers" }
        check(voices.map { it.speakerId } == (0..102).toList()) {
            "Kokoro v1.1 speaker IDs must remain contiguous"
        }
    }

    fun speakerId(voiceId: String): Int = voices.firstOrNull { it.id == voiceId }?.speakerId
        ?: voices.first { it.id == DEFAULT_VOICE_ID }.speakerId

    fun voiceFor(voiceId: String): KokoroVoice = voices.firstOrNull { it.id == voiceId }
        ?: voices.first { it.id == DEFAULT_VOICE_ID }
}

/**
 * Owns the complete lifecycle for Nastech's app-private Kokoro packages. Downloads only begin after
 * an explicit user action. Archives are size and SHA-256 verified, safely extracted into a staging
 * directory, checked for all runtime and license files, and moved into place atomically.
 */
class KokoroPackageManager(context: Context) {
    private val packageRoot = File(context.applicationContext.filesDir, "kokoro-models")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var downloadJob: Job? = null
    private var activeVariant = KokoroPackageVariant.INT8

    private val _state = MutableStateFlow<KokoroPackageState>(scanInstalledPackage(activeVariant))
    val state: StateFlow<KokoroPackageState> = _state.asStateFlow()

    fun selectPackage(modelPackage: KokoroPackageVariant) {
        if (downloadJob?.isActive == true) return
        activeVariant = modelPackage
        _state.value = scanInstalledPackage(modelPackage)
    }

    fun refresh(modelPackage: KokoroPackageVariant = activeVariant) {
        if (downloadJob?.isActive == true) return
        activeVariant = modelPackage
        _state.value = scanInstalledPackage(modelPackage)
    }

    fun download(modelPackage: KokoroPackageVariant = activeVariant) {
        if (downloadJob?.isActive == true) return
        activeVariant = modelPackage
        downloadJob = scope.launch {
            try {
                downloadAndInstall(modelPackage)
            } catch (error: CancellationException) {
                _state.value = scanInstalledPackage(modelPackage)
                throw error
            } catch (error: Throwable) {
                _state.value = KokoroPackageState.Error(
                    modelPackage,
                    error.message ?: "The local voice package could not be installed",
                )
            }
        }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
        _state.value = scanInstalledPackage(activeVariant)
    }

    fun remove(modelPackage: KokoroPackageVariant = activeVariant) {
        if (modelPackage == activeVariant) cancelDownload()
        packageDirectory(modelPackage).deleteRecursively()
        archiveFile(modelPackage).delete()
        partialArchiveFile(modelPackage).delete()
        if (modelPackage == activeVariant) {
            _state.value = KokoroPackageState.NotDownloaded(modelPackage)
        }
    }

    fun readyDirectory(modelPackage: KokoroPackageVariant): File? = packageDirectory(modelPackage)
        .takeIf { isVerifiedPackage(it, modelPackage) }

    fun isReady(modelPackage: KokoroPackageVariant): Boolean = readyDirectory(modelPackage) != null

    fun close() {
        cancelDownload()
        scope.cancel()
    }

    private suspend fun downloadAndInstall(modelPackage: KokoroPackageVariant) {
        packageRoot.mkdirs()
        val partial = partialArchiveFile(modelPackage)
        if (partial.length() >= modelPackage.archiveBytes) partial.delete()
        val existing = partial.takeIf { it.isFile }?.length() ?: 0L
        val connection = (URL(modelPackage.archiveUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("Accept", "application/octet-stream")
            setRequestProperty("User-Agent", "Nastech-LocalVoice-Downloader")
            if (existing > 0L) setRequestProperty("Range", "bytes=$existing-")
        }
        try {
            val response = connection.responseCode
            if (response !in listOf(HttpURLConnection.HTTP_OK, HttpURLConnection.HTTP_PARTIAL)) {
                error("Voice package download failed (HTTP $response)")
            }
            val resume = response == HttpURLConnection.HTTP_PARTIAL && existing > 0L
            if (!resume) partial.delete()
            val writtenBefore = if (resume) existing else 0L
            val contentBytes = connection.contentLengthLong.takeIf { it > 0L } ?: 0L
            val totalBytes = if (contentBytes > 0L) writtenBefore + contentBytes else modelPackage.archiveBytes
            connection.inputStream.use { input ->
                FileOutputStream(partial, resume).use { output ->
                    val buffer = ByteArray(BUFFER_BYTES)
                    var downloaded = writtenBefore
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        downloaded += count
                        _state.value = KokoroPackageState.Downloading(modelPackage, downloaded, totalBytes)
                    }
                    output.fd.sync()
                }
            }
        } finally {
            connection.disconnect()
        }

        if (partial.length() != modelPackage.archiveBytes) {
            error("The downloaded voice package size did not match the expected archive")
        }
        _state.value = KokoroPackageState.Verifying(modelPackage)
        if (!sha256(partial).equals(modelPackage.archiveSha256, ignoreCase = true)) {
            partial.delete()
            error("The downloaded voice package failed integrity verification")
        }

        val archive = archiveFile(modelPackage)
        if (archive.exists()) archive.delete()
        if (!partial.renameTo(archive)) {
            partial.copyTo(archive, overwrite = true)
            partial.delete()
        }

        val staging = File(packageRoot, ".${modelPackage.directoryName}-staging")
        staging.deleteRecursively()
        staging.mkdirs()
        try {
            extractSafely(archive, staging)
            val extracted = File(staging, modelPackage.directoryName)
            if (!isModelLayoutComplete(extracted, modelPackage)) {
                error("The verified voice package is missing required speech assets")
            }
            val destination = packageDirectory(modelPackage)
            destination.deleteRecursively()
            if (!extracted.renameTo(destination)) {
                extracted.copyRecursively(destination, overwrite = true)
                extracted.deleteRecursively()
            }
            File(destination, READY_MARKER).writeText("${modelPackage.id}\n${modelPackage.archiveSha256}\n")
            archive.delete()
            _state.value = KokoroPackageState.Ready(
                modelPackage = modelPackage,
                directory = destination,
                installedBytes = installedSize(destination),
            )
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
                    if (unpackedBytes > MAX_UNPACKED_BYTES) {
                        error("Voice package exceeds safe extraction size")
                    }
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

    private fun scanInstalledPackage(modelPackage: KokoroPackageVariant): KokoroPackageState {
        val directory = packageDirectory(modelPackage)
        return if (isVerifiedPackage(directory, modelPackage)) {
            KokoroPackageState.Ready(modelPackage, directory, installedSize(directory))
        } else {
            KokoroPackageState.NotDownloaded(modelPackage)
        }
    }

    private fun isVerifiedPackage(directory: File, modelPackage: KokoroPackageVariant): Boolean {
        val marker = File(directory, READY_MARKER)
        val expectedMarker = "${modelPackage.id}\n${modelPackage.archiveSha256}"
        return marker.isFile &&
            marker.readText().trim() == expectedMarker &&
            isModelLayoutComplete(directory, modelPackage)
    }

    private fun isModelLayoutComplete(directory: File, modelPackage: KokoroPackageVariant): Boolean =
        requiredModelFiles(modelPackage).all { relativePath -> File(directory, relativePath).isFile }

    private fun requiredModelFiles(modelPackage: KokoroPackageVariant): List<String> = listOf(
        modelPackage.modelFileName,
        "voices.bin",
        "tokens.txt",
        "lexicon-us-en.txt",
        "lexicon-zh.txt",
        "espeak-ng-data/phondata",
        "README.md",
        "LICENSE",
    )

    private fun packageDirectory(modelPackage: KokoroPackageVariant): File =
        File(packageRoot, modelPackage.directoryName)

    private fun archiveFile(modelPackage: KokoroPackageVariant): File =
        File(packageRoot, "${modelPackage.directoryName}.tar.bz2")

    private fun partialArchiveFile(modelPackage: KokoroPackageVariant): File =
        File(packageRoot, "${modelPackage.directoryName}.tar.bz2.partial")

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
    }
}
