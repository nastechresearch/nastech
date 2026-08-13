package io.github.nastechresearch.nastech.ui.pages.extensions.skills

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import io.github.nastechresearch.nastech.R
import io.github.nastechresearch.nastech.data.files.SkillFrontmatterParser
import io.github.nastechresearch.nastech.data.files.SkillManager
import io.github.nastechresearch.nastech.skills.GitHubSkillImporter
import java.io.File

data class SkillFile(
    val file: File,
    val relativePath: String,
)

data class SkillSourceMetadata(
    val sourceUrl: String,
    val lastUpdatedMillis: Long,
)

sealed class SkillFileNode {
    data class FileNode(val skillFile: SkillFile) : SkillFileNode()
    data class DirNode(
        val name: String,
        val relativePath: String,
        val children: List<SkillFileNode>,
    ) : SkillFileNode()
}

class SkillDetailVM(
    private val context: Context,
    private val skillManager: SkillManager,
    private val gitHubImporter: GitHubSkillImporter,
) : ViewModel() {

    private val _tree = MutableStateFlow<List<SkillFileNode>>(emptyList())
    val tree = _tree.asStateFlow()

    private val _source = MutableStateFlow<SkillSourceMetadata?>(null)
    val source = _source.asStateFlow()

    private var skillName = ""

    fun init(name: String) {
        if (skillName == name) return
        skillName = name
        loadFiles()
    }

    fun loadFiles() {
        viewModelScope.launch(Dispatchers.IO) {
            val dir = skillManager.getSkillDir(skillName)
            if (dir == null) {
                _tree.value = emptyList()
                _source.value = null
                return@launch
            }
            _tree.value = buildTree(dir, dir)
            val skillFile = File(dir, "SKILL.md")
            val sourceUrl = skillFile.takeIf { it.isFile }
                ?.readText()
                ?.let(SkillFrontmatterParser::parse)
                ?.get("source-url")
                ?.takeIf { it.startsWith("https://") }
            _source.value = sourceUrl?.let {
                SkillSourceMetadata(
                    sourceUrl = it,
                    lastUpdatedMillis = skillFile.lastModified(),
                )
            }
        }
    }

    private fun buildTree(root: File, dir: File): List<SkillFileNode> {
        val items = dir.listFiles()?.toList() ?: return emptyList()
        val files = items
            .filter { it.isFile }
            .sortedWith(compareBy({ it.name != "SKILL.md" }, { it.name }))
            .map { f -> SkillFileNode.FileNode(SkillFile(f, f.relativeTo(root).path)) }
        val dirs = items
            .filter { it.isDirectory }
            .sortedBy { it.name }
            .map { d -> SkillFileNode.DirNode(d.name, d.relativeTo(root).path, buildTree(root, d)) }
        return dirs + files
    }

    fun readFile(skillFile: SkillFile): String = skillFile.file.readText()

    // Returns null on success, error message on failure
    fun saveFile(relativePath: String, content: String, onResult: (String?) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            if (relativePath == "SKILL.md") {
                val name = SkillFrontmatterParser.parse(content)["name"]
                if (name != skillName) {
                    withContext(Dispatchers.Main) {
                        onResult(context.getString(R.string.skill_detail_name_immutable, skillName))
                    }
                    return@launch
                }
            }
            val success = skillManager.saveSkillFile(skillName, relativePath, content)
            loadFiles()
            withContext(Dispatchers.Main) {
                onResult(if (success) null else context.getString(R.string.skill_detail_save_failed))
            }
        }
    }

    fun deleteFile(skillFile: SkillFile, onResult: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val success = skillManager.deleteSkillFile(skillName, skillFile.relativePath)
            if (success) loadFiles()
            withContext(Dispatchers.Main) { onResult(success) }
        }
    }

    /**
     * Explicitly refreshes a repository-installed skill. It never runs in the background and
     * keeps the current skill name as a hard identity check before the shared importer swaps the
     * directory atomically.
     */
    fun refreshFromSource(onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val currentSource = _source.value
            if (currentSource == null) {
                withContext(Dispatchers.Main) {
                    onResult(false, "This skill does not have a GitHub source to refresh")
                }
                return@launch
            }
            when (val result = gitHubImporter.importFromDirectory(
                sourceUrl = currentSource.sourceUrl,
                expectedSkillName = skillName,
            )) {
                is GitHubSkillImporter.Result.Ok -> {
                    loadFiles()
                    withContext(Dispatchers.Main) {
                        onResult(true, "Updated from source")
                    }
                }

                is GitHubSkillImporter.Result.Err -> withContext(Dispatchers.Main) {
                    onResult(false, result.detail)
                }
            }
        }
    }
}
