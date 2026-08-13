package io.github.nastechresearch.nastech.skills

import io.github.nastechresearch.nastech.data.files.SkillFrontmatterParser
import io.github.nastechresearch.nastech.data.files.SkillManager
import java.net.HttpURLConnection
import java.net.URL
import java.util.LinkedHashMap
import org.json.JSONArray

/**
 * Imports one public GitHub directory containing [SKILL.md] through the GitHub Contents API.
 *
 * This is deliberately a data-only importer. It downloads text assets, validates the skill
 * metadata, stores the original HTTPS source in frontmatter, and atomically replaces the one
 * installed skill directory. It never executes repository code or updates skills in background.
 */
class GitHubSkillImporter(
    private val skillManager: SkillManager,
) {
    sealed interface Result {
        data class Ok(
            val skillName: String,
            val sourceUrl: String,
        ) : Result

        data class Err(val detail: String) : Result
    }

    fun importFromDirectory(
        sourceUrl: String,
        expectedSkillName: String? = null,
    ): Result {
        val info = parseGitHubUrl(sourceUrl)
            ?: return Result.Err("Enter a public GitHub repository or directory URL")

        return try {
            val files = mutableListOf<Pair<String, String>>()
            if (!listFilesRecursively(info.owner, info.repo, info.branch, info.path, info.path, files)) {
                return Result.Err("Could not list the GitHub directory contents")
            }

            val skillMdEntry = files.find { it.first == "SKILL.md" }
                ?: return Result.Err("No SKILL.md was found in the selected GitHub directory")
            val rawSkillMd = downloadText(skillMdEntry.second)
                ?: return Result.Err("Could not download SKILL.md. Check the source URL and connection")
            val name = SkillFrontmatterParser.parse(rawSkillMd)["name"]
                ?.takeIf { it.isNotBlank() }
                ?: return Result.Err("SKILL.md is missing the required name field")
            if (expectedSkillName != null && name != expectedSkillName) {
                return Result.Err("The source now identifies a different skill and was not installed")
            }

            val contents = LinkedHashMap<String, String>()
            for ((relativePath, downloadUrl) in files) {
                val content = downloadText(downloadUrl)
                    ?: return Result.Err("Could not download $relativePath")
                contents[relativePath] = if (relativePath == "SKILL.md") {
                    ensureSourceUrl(content, sourceUrl.trim())
                } else {
                    content
                }
            }

            if (!skillManager.saveSkillFilesAtomically(name, contents)) {
                return Result.Err("Could not safely save the downloaded skill files")
            }
            Result.Ok(skillName = name, sourceUrl = sourceUrl.trim())
        } catch (error: Exception) {
            Result.Err(error.message ?: "GitHub skill import failed")
        }
    }

    private fun listFilesRecursively(
        owner: String,
        repo: String,
        branch: String,
        directoryPath: String,
        basePath: String,
        result: MutableList<Pair<String, String>>,
    ): Boolean {
        val encodedPath = directoryPath.split('/').joinToString("/") { segment ->
            java.net.URLEncoder.encode(segment, Charsets.UTF_8.name())
        }
        val apiUrl = "https://api.github.com/repos/$owner/$repo/contents/$encodedPath?ref=" +
            java.net.URLEncoder.encode(branch, Charsets.UTF_8.name())
        val json = downloadText(apiUrl) ?: return false
        val array = runCatching { JSONArray(json) }.getOrNull() ?: return false
        for (index in 0 until array.length()) {
            val item = array.getJSONObject(index)
            val type = item.getString("type")
            val itemPath = item.getString("path")
            val relativePath = itemPath.removePrefix("$basePath/").removePrefix(basePath)
            when (type) {
                "file" -> {
                    val downloadUrl = item.optString("download_url").takeIf { it.isNotBlank() }
                        ?: return false
                    result += relativePath to downloadUrl
                }

                "dir" -> if (!listFilesRecursively(owner, repo, branch, itemPath, basePath, result)) {
                    return false
                }
            }
        }
        return true
    }

    private fun parseGitHubUrl(sourceUrl: String): GitHubRepoInfo? {
        val trimmed = sourceUrl.trim().trimEnd('/')
        val match = GITHUB_DIRECTORY_URL.matchEntire(trimmed) ?: return null
        return GitHubRepoInfo(
            owner = match.groupValues[1],
            repo = match.groupValues[2],
            branch = match.groupValues[3].ifBlank { "HEAD" },
            path = match.groupValues[4].trimStart('/'),
        )
    }

    private fun downloadText(url: String): String? {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = READ_TIMEOUT_MS
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        connection.setRequestProperty("User-Agent", "Nastech-Skill-Importer")
        return try {
            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                null
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun ensureSourceUrl(content: String, sourceUrl: String): String {
        if (!content.startsWith("---")) return content
        if (SOURCE_URL_LINE.containsMatchIn(content)) {
            return SOURCE_URL_LINE.replace(content, "source-url: $sourceUrl")
        }
        val closing = FRONTMATTER_END.find(content, startIndex = 3) ?: return content
        return buildString(content.length + sourceUrl.length + 16) {
            append(content, 0, closing.range.first)
            append("\nsource-url: ")
            append(sourceUrl)
            append(content, closing.range.first, content.length)
        }
    }

    private data class GitHubRepoInfo(
        val owner: String,
        val repo: String,
        val branch: String,
        val path: String,
    )

    private companion object {
        const val CONNECT_TIMEOUT_MS = 10_000
        const val READ_TIMEOUT_MS = 30_000
        val GITHUB_DIRECTORY_URL = Regex(
            """https://github\\.com/([A-Za-z0-9_.-]+)/([A-Za-z0-9_.-]+)(?:/tree/([^/]+)(/.*)?)?"""
        )
        val SOURCE_URL_LINE = Regex("""(?m)^source-url:\\s*.*$""")
        val FRONTMATTER_END = Regex("""\\r?\\n---(?:\\r?\\n|$)""")
    }
}
