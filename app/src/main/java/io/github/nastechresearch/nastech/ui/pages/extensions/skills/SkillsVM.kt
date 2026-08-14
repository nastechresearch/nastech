package io.github.nastechresearch.nastech.ui.pages.extensions.skills

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import io.github.nastechresearch.nastech.data.files.SkillFrontmatterParser
import io.github.nastechresearch.nastech.data.files.SkillManager
import io.github.nastechresearch.nastech.data.files.SkillMetadata
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withTimeoutOrNull
import io.github.nastechresearch.nastech.skills.CatalogEntry
import io.github.nastechresearch.nastech.skills.GitHubSkillImporter
import io.github.nastechresearch.nastech.skills.SkillCatalog
import io.github.nastechresearch.nastech.skills.SkillUrlImporter
import io.github.nastechresearch.nastech.skills.SkillZipError
import io.github.nastechresearch.nastech.skills.SkillZipImporter
import io.github.nastechresearch.nastech.skills.loadCatalogFromAssets
import io.github.nastechresearch.nastech.skills.parseSkillCatalogJson
import java.util.LinkedHashMap
import org.json.JSONArray
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import kotlin.collections.iterator

class SkillsVM(
    private val context: Context,
    private val skillManager: SkillManager,
    private val urlImporter: SkillUrlImporter,
    private val gitHubImporter: GitHubSkillImporter,
) : ViewModel() {

    companion object {
        private const val TAG = "SkillsVM"
        private const val MAX_MD_BYTES = 1L * 1024 * 1024 // 1 MB cap on local .md
        private const val MAX_CATALOG_BYTES = 1L * 1024 * 1024
        private const val MAX_CATALOG_ENTRIES = 500
        private const val CATALOG_CACHE_FILE = "skill-catalog.json"
        private const val CURATED_CATALOG_URL =
            "https://raw.githubusercontent.com/nastechresearch/nastech/main/app/src/main/assets/skill-catalog.json"
        private const val MAX_BATCH_INSTALLS = 20
    }

    data class BatchInstallReport(
        val requested: Int,
        val installed: Int,
        val failed: List<String>,
    )
    private val _skills = MutableStateFlow<List<SkillMetadata>>(emptyList())
    val skills = _skills.asStateFlow()

    /**
     * Phase 19D — flow-derived snapshot of currently-installed skill names. The catalog
     * sheet observes this so the "Install" / "Installed" button state stays in sync as
     * the user (or LLM) installs / deletes skills.
     */
    val installedSkillNames = _skills
        .map { list -> list.mapTo(mutableSetOf()) { it.name } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    /**
     * Curated catalogue snapshot. The bundled asset is the offline baseline; a successful
     * user-triggered refresh replaces this snapshot with a verified cache from Nastech's source.
     */
    private val _catalog = MutableStateFlow(loadCachedCatalog() ?: loadCatalogFromAssets(context))
    val catalog = _catalog.asStateFlow()

    init {
        loadSkills()
    }

    private fun loadSkills() {
        viewModelScope.launch(Dispatchers.IO) {
            _skills.value = skillManager.listSkills()
        }
    }

    fun saveSkill(name: String, content: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = skillManager.saveSkill(name, content)
            _skills.value = skillManager.listSkills()
            withContext(Dispatchers.Main) {
                onResult(result != null)
            }
        }
    }

    fun deleteSkill(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            skillManager.deleteSkill(name)
            _skills.value = skillManager.listSkills()
        }
    }

    fun getSkillsDir() = skillManager.getSkillsDir()

    /**
     * Refreshes only catalogue descriptions and source links from Nastech's public index.
     * This never installs a skill, executes a repository, or changes an installed skill.
     */
    fun refreshCatalog(onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val raw = downloadCatalog(CURATED_CATALOG_URL)
            val parsed = raw?.let(::parseSkillCatalogJson)
            val validationError = parsed?.let(::validateCatalog)
            if (parsed == null || validationError != null) {
                withContext(Dispatchers.Main) {
                    onResult(false, validationError ?: "Could not refresh the catalogue")
                }
                return@launch
            }
            val cache = File(context.filesDir, CATALOG_CACHE_FILE)
            val temp = File(context.filesDir, "$CATALOG_CACHE_FILE.tmp")
            runCatching {
                temp.writeText(raw)
                if (!temp.renameTo(cache)) {
                    cache.delete()
                    if (!temp.renameTo(cache)) error("Could not save the refreshed catalogue")
                }
            }.onFailure {
                temp.delete()
                withContext(Dispatchers.Main) { onResult(false, "Could not save the refreshed catalogue") }
                return@launch
            }
            _catalog.value = parsed
            withContext(Dispatchers.Main) { onResult(true, "Sources refreshed") }
        }
    }

    fun importSkillFromGitHub(repoUrl: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = gitHubImporter.importFromDirectory(repoUrl)
            _skills.value = skillManager.listSkills()
            withContext(Dispatchers.Main) {
                when (result) {
                    is GitHubSkillImporter.Result.Ok -> onResult(true, result.skillName)
                    is GitHubSkillImporter.Result.Err -> onResult(false, result.detail)
                }
            }
        }
    }

    /**
     * Phase 19C — install a skill from a local file picked via SAF (`OpenDocument`).
     *
     * Accepts:
     *  - `.md` / `.markdown` (or `text/markdown` MIME): read up to [MAX_MD_BYTES] of UTF-8
     *    text, then run through [SkillUrlImporter.importFromText] (same format detection
     *    + HTML guard + transcoder pipeline as the GitHub URL path).
     *  - `.zip` (or `application/zip` MIME): extract via [SkillZipImporter] to a temp dir
     *    inside the app's cache, locate the SKILL.md, copy every file inside that root
     *    into the SkillManager via [SkillManager.saveSkillFilesAtomically].
     *
     * On failure, [onResult] receives `false` + a localised-string-key (`skill_import_*`)
     * the UI looks up via stringResource. On success, [onResult] receives `true` + the
     * installed skill's name.
     */
    fun importFromLocalFile(uri: Uri, onResult: (success: Boolean, message: String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val type = detectFileType(uri)
            try {
                val outcome: Pair<Boolean, String> = when (type) {
                    LocalFileType.Markdown -> importLocalMarkdown(uri)
                    LocalFileType.Zip -> importLocalZip(uri)
                    LocalFileType.Unsupported -> false to "skill_import_unsupported_file_type"
                }
                _skills.value = skillManager.listSkills()
                withContext(Dispatchers.Main) { onResult(outcome.first, outcome.second) }
            } catch (t: Throwable) {
                Log.w(TAG, "importFromLocalFile failed for $uri", t)
                withContext(Dispatchers.Main) {
                    onResult(false, t.message ?: "skill_import_unsupported_file_type")
                }
            }
        }
    }

    /**
     * Phase 19D — install a skill from a [CatalogEntry].
     *
     * If the entry is `is_bundled = true`, this is a no-op (the skill is already on disk
     * via [SkillManager.seedDefaultSkillsIfNeeded]) and we return success immediately so
     * the UI flips its row to "Installed". Otherwise [CatalogEntry.sourceUrl] is fetched
     * via [SkillUrlImporter.importFromUrl] under a 30-second hard timeout — same surface
     * as the existing GitHub-URL import path, including HTML guard + format detector.
     */
    fun installFromCatalog(entry: CatalogEntry, onResult: (success: Boolean, message: String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val (ok, message) = installCatalogEntry(entry)
            _skills.value = skillManager.listSkills()
            withContext(Dispatchers.Main) { onResult(ok, message) }
        }
    }

    /**
     * Installs a user-selected collection from the already validated catalogue. Downloads are
     * deliberately sequential: this avoids uncontrolled network fan-out and preserves clear
     * per-skill failure reporting when one source is unavailable.
     */
    fun installFromCatalogBatch(entries: List<CatalogEntry>, onResult: (BatchInstallReport) -> Unit) {
        val batch = entries.distinctBy { it.name }.take(MAX_BATCH_INSTALLS)
        viewModelScope.launch(Dispatchers.IO) {
            val failures = mutableListOf<String>()
            var installed = 0
            batch.forEach { entry ->
                val (ok, detail) = installCatalogEntry(entry)
                if (ok) installed++ else failures += "${entry.name}: $detail"
            }
            _skills.value = skillManager.listSkills()
            withContext(Dispatchers.Main) {
                onResult(BatchInstallReport(batch.size, installed, failures))
            }
        }
    }

    private suspend fun installCatalogEntry(entry: CatalogEntry): Pair<Boolean, String> {
        if (entry.isBundled) return true to entry.name
        val url = entry.sourceUrl ?: return false to "skill_catalog_install_failed"
        val result = withTimeoutOrNull(30_000) { urlImporter.importFromUrl(url) }
        return when (result) {
            null -> false to "skill_catalog_install_failed"
            is SkillUrlImporter.Result.Ok -> true to result.metadata.name
            is SkillUrlImporter.Result.Err -> false to result.detail
        }
    }

    private fun loadCachedCatalog(): SkillCatalog? {
        val cache = File(context.filesDir, CATALOG_CACHE_FILE)
        if (!cache.isFile || cache.length() > MAX_CATALOG_BYTES) return null
        val catalog = runCatching { parseSkillCatalogJson(cache.readText()) }.getOrNull() ?: return null
        return catalog.takeIf { validateCatalog(it) == null }
    }

    private fun downloadCatalog(url: String): String? {
        val connection = (URL(url).openConnection() as? HttpURLConnection) ?: return null
        connection.connectTimeout = 10_000
        connection.readTimeout = 30_000
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("User-Agent", "Nastech-Skill-Catalog")
        return try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
            connection.inputStream.use { input ->
                val bytes = input.readBytes()
                if (bytes.size > MAX_CATALOG_BYTES) null else bytes.toString(Charsets.UTF_8)
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun validateCatalog(candidate: SkillCatalog): String? {
        if (candidate.skills.isEmpty()) return "The refreshed catalogue did not contain any skills"
        if (candidate.skills.size > MAX_CATALOG_ENTRIES) return "The refreshed catalogue is too large"
        if (candidate.skills.any { entry ->
                entry.name.isBlank() || entry.title.isBlank() ||
                    (entry.sourceUrl != null && !entry.sourceUrl.startsWith("https://"))
            }
        ) {
            return "The refreshed catalogue contains an invalid entry"
        }
        if (candidate.skills.map { it.name }.toSet().size != candidate.skills.size) {
            return "The refreshed catalogue contains duplicate skill names"
        }
        return null
    }

    private enum class LocalFileType { Markdown, Zip, Unsupported }

    private fun detectFileType(uri: Uri): LocalFileType {
        val mime = context.contentResolver.getType(uri)?.lowercase()
        if (mime != null) {
            if (mime == "text/markdown" || mime == "text/x-markdown" || mime == "text/plain") {
                return LocalFileType.Markdown
            }
            if (mime == "application/zip" || mime == "application/x-zip-compressed") {
                return LocalFileType.Zip
            }
        }
        // Fall back to the displayed filename. Some pickers (e.g. Files by Google) don't
        // attach a MIME type for `.md` and surface it as `application/octet-stream`.
        val name = queryDisplayName(uri)?.lowercase().orEmpty()
        return when {
            name.endsWith(".md") || name.endsWith(".markdown") -> LocalFileType.Markdown
            name.endsWith(".zip") -> LocalFileType.Zip
            else -> LocalFileType.Unsupported
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        return runCatching {
            context.contentResolver.query(uri, arrayOf("_display_name"), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull()
    }

    private fun importLocalMarkdown(uri: Uri): Pair<Boolean, String> {
        val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
            // Read up to MAX_MD_BYTES + 1 to detect overflow without materialising the
            // whole stream blindly.
            val out = java.io.ByteArrayOutputStream()
            val buf = ByteArray(8 * 1024)
            var total = 0L
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                total += n
                if (total > MAX_MD_BYTES) {
                    // Use the markdown-specific cap error, not the zip cap key.
                    return false to "skill_import_md_too_large"
                }
                out.write(buf, 0, n)
            }
            out.toByteArray()
        } ?: return false to "skill_import_unsupported_file_type"
        val text = bytes.toString(Charsets.UTF_8)
        if (text.isBlank()) {
            return false to "skill_import_empty_file"
        }
        val sourceLabel = queryDisplayName(uri) ?: "local_file"
        val result = urlImporter.importFromText(text, sourceLabel = sourceLabel)
        return when (result) {
            is SkillUrlImporter.Result.Ok -> true to result.metadata.name
            is SkillUrlImporter.Result.Err -> false to result.detail
        }
    }

    private fun importLocalZip(uri: Uri): Pair<Boolean, String> {
        // Extract into a uniquely-named temp dir under cache so we never collide with
        // another import-in-flight. We delete it on success or failure.
        val tempRoot = File(context.cacheDir, "skill-zip-import")
        tempRoot.mkdirs()
        val workDir = Files.createTempDirectory(tempRoot.toPath(), "extract-").toFile()
        try {
            val skillRoot = context.contentResolver.openInputStream(uri)?.use { input ->
                SkillZipImporter.extractZipToDir(input, workDir)
            } ?: return false to "skill_import_unsupported_file_type"
            if (skillRoot.isFailure) {
                val err = skillRoot.exceptionOrNull()
                val key = when (err) {
                    is SkillZipError.MissingSkillMd -> "skill_import_missing_skill_md"
                    is SkillZipError.PathTraversal -> "skill_import_path_traversal"
                    is SkillZipError.TooLarge -> "skill_import_zip_too_large"
                    else -> "skill_import_unsupported_file_type"
                }
                return false to key
            }
            val rootDir = skillRoot.getOrThrow()
            val skillMd = rootDir.resolve("SKILL.md").takeIf { it.exists() }
                ?: rootDir.listFiles()?.firstOrNull { it.isFile && it.name.equals("SKILL.md", ignoreCase = true) }
                ?: return false to "skill_import_missing_skill_md"
            val frontmatter = SkillFrontmatterParser.parse(skillMd.readText())
            val skillName = frontmatter["name"]?.takeIf { it.isNotBlank() }
                ?: return false to "skill_import_missing_skill_md"
            // Collect every file into a relativePath -> content map, then atomic-save.
            val files = LinkedHashMap<String, String>()
            rootDir.walkTopDown().filter { it.isFile }.forEach { f ->
                val rel = f.relativeTo(rootDir).path.replace(File.separatorChar, '/')
                files[rel] = f.readText()
            }
            val saved = skillManager.saveSkillFilesAtomically(skillName, files)
            return if (saved) true to skillName else false to "skill_import_unsupported_file_type"
        } finally {
            runCatching { workDir.deleteRecursively() }
        }
    }

    private fun listFilesRecursively(
        owner: String,
        repo: String,
        branch: String,
        dirPath: String,
        basePath: String,
        result: MutableList<Pair<String, String>>,
    ): Boolean {
        val apiUrl = "https://api.github.com/repos/$owner/$repo/contents/$dirPath?ref=$branch"
        val json = downloadText(apiUrl) ?: return false
        val array = JSONArray(json)
        for (i in 0 until array.length()) {
            val item = array.getJSONObject(i)
            val type = item.getString("type")
            val itemPath = item.getString("path")
            val relativePath = itemPath.removePrefix("$basePath/").removePrefix(basePath)
            when (type) {
                "file" -> {
                    val downloadUrl = item.optString("download_url").takeIf { it.isNotBlank() }
                        ?: return false
                    result.add(relativePath to downloadUrl)
                }

                "dir" -> {
                    val ok = listFilesRecursively(owner, repo, branch, itemPath, basePath, result)
                    if (!ok) return false
                }
            }
        }
        return true
    }

    private data class GitHubRepoInfo(
        val owner: String,
        val repo: String,
        val branch: String,
        val path: String,
    )

    private fun parseGitHubUrl(url: String): GitHubRepoInfo? {
        val trimmed = url.trim().trimEnd('/')
        // https://github.com/owner/repo
        // https://github.com/owner/repo/tree/branch
        // https://github.com/owner/repo/tree/branch/sub/path
        val regex = Regex("""https://github\.com/([^/]+)/([^/]+)(?:/tree/([^/]+)(/.*)?)?""")
        val match = regex.matchEntire(trimmed) ?: return null
        val owner = match.groupValues[1]
        val repo = match.groupValues[2]
        val branch = match.groupValues[3].ifBlank { "HEAD" }
        val subPath = match.groupValues[4].trimStart('/')
        return GitHubRepoInfo(owner, repo, branch, subPath)
    }

    private fun downloadText(url: String): String? {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 30_000
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        return try {
            if (connection.responseCode == 200) connection.inputStream.bufferedReader().readText()
            else null
        } finally {
            connection.disconnect()
        }
    }
}
