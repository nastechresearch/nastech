package me.rerere.asr

import android.content.Context
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
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

/** Verified, app-private package manager for Nastech's local streaming speech recognition models. */
enum class LocalAsrModelPackage(
    val id: String,
    val displayName: String,
    val description: String,
    val directoryName: String,
    val archiveUrl: String,
    val archiveBytes: Long,
    val archiveSha256: String,
    val encoderFile: String,
    val decoderFile: String,
    val joinerFile: String,
) {
    ENGLISH_STREAMING_INT8(
        id = "zipformer-en-20m-int8",
        displayName = "English streaming · INT8",
        description = "103 MB download. Fast offline English dictation with live partial text.",
        directoryName = "sherpa-onnx-streaming-zipformer-en-20M-2023-02-17-mobile",
        archiveUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-streaming-zipformer-en-20M-2023-02-17-mobile.tar.bz2",
        archiveBytes = 107_569_151L,
        archiveSha256 = "753a5362538539212442efbfb8dd5748db82e35e7deaec5330f49c56e81e40fd",
        encoderFile = "encoder-epoch-99-avg-1.int8.onnx",
        decoderFile = "decoder-epoch-99-avg-1.onnx",
        joinerFile = "joiner-epoch-99-avg-1.int8.onnx",
    ),
    BILINGUAL_ZH_EN_INT8(
        id = "zipformer-zh-en-int8",
        displayName = "English + Chinese streaming · INT8",
        description = "347 MB download. Fully local live English and Chinese dictation.",
        directoryName = "sherpa-onnx-streaming-zipformer-bilingual-zh-en-2023-02-20-mobile",
        archiveUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-streaming-zipformer-bilingual-zh-en-2023-02-20-mobile.tar.bz2",
        archiveBytes = 346_965_352L,
        archiveSha256 = "45b8d04fe8faf5146397ff2e71d90ca8effdfcbe9adc30fdce769e92cabd6b03",
        encoderFile = "encoder-epoch-99-avg-1.int8.onnx",
        decoderFile = "decoder-epoch-99-avg-1.onnx",
        joinerFile = "joiner-epoch-99-avg-1.int8.onnx",
    ),
    ;

    companion object {
        fun fromId(id: String?): LocalAsrModelPackage = entries.firstOrNull { it.id == id }
            ?: ENGLISH_STREAMING_INT8
    }
}

sealed interface LocalAsrPackageState {
    val modelPackage: LocalAsrModelPackage

    data class NotDownloaded(override val modelPackage: LocalAsrModelPackage) : LocalAsrPackageState
    data class Downloading(
        override val modelPackage: LocalAsrModelPackage,
        val downloadedBytes: Long,
        val totalBytes: Long,
    ) : LocalAsrPackageState
    data class Verifying(override val modelPackage: LocalAsrModelPackage) : LocalAsrPackageState
    data class Ready(
        override val modelPackage: LocalAsrModelPackage,
        val directory: File,
        val installedBytes: Long,
    ) : LocalAsrPackageState
    data class Error(
        override val modelPackage: LocalAsrModelPackage,
        val message: String,
    ) : LocalAsrPackageState
}

class LocalAsrPackageManager(context: Context) {
    private val packageRoot = File(context.applicationContext.filesDir, "local-asr-models")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var downloadJob: Job? = null
    private var activePackage = LocalAsrModelPackage.ENGLISH_STREAMING_INT8

    private val _state = MutableStateFlow<LocalAsrPackageState>(scan(activePackage))
    val state: StateFlow<LocalAsrPackageState> = _state.asStateFlow()

    fun select(modelPackage: LocalAsrModelPackage) {
        if (downloadJob?.isActive == true) return
        activePackage = modelPackage
        _state.value = scan(modelPackage)
    }

    fun refresh(modelPackage: LocalAsrModelPackage = activePackage) {
        if (downloadJob?.isActive == true) return
        activePackage = modelPackage
        _state.value = scan(modelPackage)
    }

    fun download(modelPackage: LocalAsrModelPackage = activePackage) {
        if (downloadJob?.isActive == true) return
        activePackage = modelPackage
        downloadJob = scope.launch {
            try {
                downloadAndInstall(modelPackage)
            } catch (error: CancellationException) {
                _state.value = scan(modelPackage)
                throw error
            } catch (error: Throwable) {
                _state.value = LocalAsrPackageState.Error(
                    modelPackage,
                    error.message ?: "The local speech model could not be installed",
                )
            }
        }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
        _state.value = scan(activePackage)
    }

    fun remove(modelPackage: LocalAsrModelPackage = activePackage) {
        if (modelPackage == activePackage) cancelDownload()
        directory(modelPackage).deleteRecursively()
        archive(modelPackage).delete()
        partialArchive(modelPackage).delete()
        if (modelPackage == activePackage) _state.value = LocalAsrPackageState.NotDownloaded(modelPackage)
    }

    fun readyDirectory(modelPackage: LocalAsrModelPackage): File? = directory(modelPackage)
        .takeIf { verified(it, modelPackage) }

    fun close() {
        cancelDownload()
        scope.cancel()
    }

    private suspend fun downloadAndInstall(modelPackage: LocalAsrModelPackage) {
        packageRoot.mkdirs()
        val partial = partialArchive(modelPackage)
        if (partial.length() >= modelPackage.archiveBytes) partial.delete()
        val existing = partial.takeIf(File::isFile)?.length() ?: 0L
        val connection = (URL(modelPackage.archiveUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("Accept", "application/octet-stream")
            setRequestProperty("User-Agent", "Nastech-LocalSpeech-Downloader")
            if (existing > 0L) setRequestProperty("Range", "bytes=$existing-")
        }
        try {
            val response = connection.responseCode
            if (response !in listOf(HttpURLConnection.HTTP_OK, HttpURLConnection.HTTP_PARTIAL)) {
                error("Speech model download failed (HTTP $response)")
            }
            val resume = response == HttpURLConnection.HTTP_PARTIAL && existing > 0L
            if (!resume) partial.delete()
            val writtenBefore = if (resume) existing else 0L
            val contentBytes = connection.contentLengthLong.takeIf { it > 0L } ?: modelPackage.archiveBytes
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
                        _state.value = LocalAsrPackageState.Downloading(
                            modelPackage,
                            downloaded,
                            writtenBefore + contentBytes,
                        )
                    }
                    output.fd.sync()
                }
            }
        } finally {
            connection.disconnect()
        }
        if (partial.length() != modelPackage.archiveBytes) error("Downloaded speech model size did not match")
        _state.value = LocalAsrPackageState.Verifying(modelPackage)
        if (!sha256(partial).equals(modelPackage.archiveSha256, ignoreCase = true)) {
            partial.delete()
            error("Downloaded speech model failed integrity verification")
        }

        val downloadedArchive = archive(modelPackage)
        downloadedArchive.delete()
        if (!partial.renameTo(downloadedArchive)) {
            partial.copyTo(downloadedArchive, overwrite = true)
            partial.delete()
        }
        val staging = File(packageRoot, ".${modelPackage.directoryName}-staging")
        staging.deleteRecursively()
        staging.mkdirs()
        try {
            extract(downloadedArchive, staging)
            val extracted = File(staging, modelPackage.directoryName)
            if (!complete(extracted, modelPackage)) error("Verified speech model is missing required files")
            val destination = directory(modelPackage)
            destination.deleteRecursively()
            if (!extracted.renameTo(destination)) {
                extracted.copyRecursively(destination, overwrite = true)
                extracted.deleteRecursively()
            }
            File(destination, READY_MARKER).writeText("${modelPackage.id}\n${modelPackage.archiveSha256}\n")
            downloadedArchive.delete()
            _state.value = LocalAsrPackageState.Ready(modelPackage, destination, installedSize(destination))
        } finally {
            staging.deleteRecursively()
        }
    }

    private fun extract(archive: File, destination: File) {
        val root = destination.canonicalFile
        var unpacked = 0L
        BZip2CompressorInputStream(FileInputStream(archive)).use { compressed ->
            TarArchiveInputStream(compressed).use { tar ->
                while (true) {
                    val entry = tar.nextTarEntry ?: break
                    if (entry.isSymbolicLink || entry.isLink) continue
                    val target = File(destination, entry.name).canonicalFile
                    if (!target.path.startsWith(root.path + File.separator)) error("Unsafe speech model archive path")
                    if (entry.isDirectory) {
                        target.mkdirs()
                        continue
                    }
                    if (entry.size !in 0..MAX_FILE_BYTES) error("Invalid speech model file size")
                    unpacked += entry.size
                    if (unpacked > MAX_UNPACKED_BYTES) error("Speech model exceeds the safe extraction limit")
                    target.parentFile?.mkdirs()
                    FileOutputStream(target).use { output ->
                        val buffer = ByteArray(BUFFER_BYTES)
                        var remaining = entry.size
                        while (remaining > 0) {
                            val count = tar.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                            if (count < 0) error("Unexpected end of speech model archive")
                            output.write(buffer, 0, count)
                            remaining -= count
                        }
                    }
                }
            }
        }
    }

    private fun scan(modelPackage: LocalAsrModelPackage): LocalAsrPackageState {
        val modelDirectory = directory(modelPackage)
        return if (verified(modelDirectory, modelPackage)) {
            LocalAsrPackageState.Ready(modelPackage, modelDirectory, installedSize(modelDirectory))
        } else {
            LocalAsrPackageState.NotDownloaded(modelPackage)
        }
    }

    private fun verified(modelDirectory: File, modelPackage: LocalAsrModelPackage): Boolean =
        File(modelDirectory, READY_MARKER).readTextOrNull()?.trim() == "${modelPackage.id}\n${modelPackage.archiveSha256}" &&
            complete(modelDirectory, modelPackage)

    private fun complete(modelDirectory: File, modelPackage: LocalAsrModelPackage): Boolean = listOf(
        modelPackage.encoderFile,
        modelPackage.decoderFile,
        modelPackage.joinerFile,
        "tokens.txt",
    ).all { File(modelDirectory, it).isFile }

    private fun directory(modelPackage: LocalAsrModelPackage) = File(packageRoot, modelPackage.directoryName)
    private fun archive(modelPackage: LocalAsrModelPackage) = File(packageRoot, "${modelPackage.directoryName}.tar.bz2")
    private fun partialArchive(modelPackage: LocalAsrModelPackage) = File(packageRoot, "${modelPackage.directoryName}.tar.bz2.partial")
    private fun installedSize(directory: File): Long = directory.walkTopDown().filter(File::isFile).sumOf(File::length)

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
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun File.readTextOrNull(): String? = runCatching { if (isFile) readText() else null }.getOrNull()

    private companion object {
        const val READY_MARKER = ".nastech-local-asr-ready"
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 45_000
        const val BUFFER_BYTES = 64 * 1024
        const val MAX_FILE_BYTES = 512L * 1024L * 1024L
        const val MAX_UNPACKED_BYTES = 700L * 1024L * 1024L
    }
}
