package io.github.nastechresearch.nastech.skills

import android.util.Log
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * Phase 19C — extract a `.zip` skill bundle into a destination directory under defensive
 * caps + path-traversal guards. Pure JVM (no Android Context required), so JVM unit tests
 * exercise the real extraction path without Robolectric.
 *
 * Caps:
 *  - **Entry count:** 200 maximum. Real-world skill zips are tiny (1–10 files); 200 is far
 *    beyond what any legitimate skill needs and is a hard ceiling against zip bombs.
 *  - **Total uncompressed bytes:** 20 MB. Stream-checked while extracting; abort + clean
 *    up if breached mid-stream.
 *
 * Path traversal: every entry name is resolved against the destination dir via canonical
 * paths. An entry whose canonical path does not start with `destDir.canonicalPath + sep`
 * is rejected — covering `../etc/passwd`, absolute paths like `/tmp/x`, and creative
 * encodings (the JDK's File canonicalisation handles `..` collapsing across all the
 * encoding tricks we care about).
 *
 * SKILL.md location: returned dir is the one that contains `SKILL.md`. Common shapes:
 *  - flat zip: `SKILL.md` at root → returns `destDir`
 *  - nested zip: `<name>/SKILL.md` → returns `destDir/<name>`
 *  - deeper nesting is supported (we walk the tree to locate SKILL.md, case-insensitively)
 *
 * Failure semantics: on any error (cap breach, traversal, IO), the destination dir is
 * deleted recursively and a `Result.failure` is returned with a typed [SkillZipError]
 * cause. The caller can `as? SkillZipError` to surface a localised string.
 */
object SkillZipImporter {

    private const val TAG = "SkillZipImporter"
    private const val MAX_ENTRIES = 200
    private const val MAX_UNCOMPRESSED_BYTES = 20L * 1024 * 1024
    private const val COPY_BUF = 8 * 1024

    /**
     * Extract [input] into [destDir]. The destination directory MUST already exist and be
     * writable; [destDir] should be a freshly-created empty temp dir owned by the caller.
     *
     * @return success: the directory that contains `SKILL.md` (could be [destDir] itself or
     *   a single nested subdirectory). failure: a [SkillZipError] describing what went wrong.
     */
    fun extractZipToDir(input: InputStream, destDir: File): Result<File> {
        if (!destDir.exists() || !destDir.isDirectory) {
            return Result.failure(SkillZipError.IoError("destination is not an existing directory"))
        }
        val destCanonical = try {
            destDir.canonicalPath
        } catch (e: IOException) {
            return Result.failure(SkillZipError.IoError(e.message ?: "canonical path failed"))
        }

        var entryCount = 0
        var totalBytes = 0L
        try {
            ZipInputStream(input).use { zis ->
                while (true) {
                    val entry = zis.nextEntry ?: break
                    entryCount++
                    if (entryCount > MAX_ENTRIES) {
                        cleanup(destDir)
                        return Result.failure(SkillZipError.TooLarge("zip exceeds $MAX_ENTRIES entries"))
                    }
                    val name = entry.name
                    // Reject empty entry names up front — they would canonicalise to
                    // destDir itself and then trip the IO write.
                    if (name.isEmpty()) {
                        cleanup(destDir)
                        return Result.failure(SkillZipError.PathTraversal(name))
                    }
                    // Reject absolute paths up front. The canonical-path check below
                    // catches `..` traversal even after the JDK normalises the entry,
                    // so this is defence-in-depth.
                    // Note: spaces in filenames are legitimate (GitHub-shaped zips often
                    // contain "repo name/SKILL.md" style entries with spaces). Spaces
                    // are intentionally NOT rejected here — the canonical-path check is
                    // the safety floor for traversal.
                    if (name.startsWith("/") || name.startsWith("\\")) {
                        cleanup(destDir)
                        return Result.failure(SkillZipError.PathTraversal(name))
                    }
                    val target = File(destDir, name)
                    val targetCanonical = try {
                        target.canonicalPath
                    } catch (e: IOException) {
                        cleanup(destDir)
                        return Result.failure(SkillZipError.IoError(e.message ?: "canonical path failed for $name"))
                    }
                    val expectedPrefix = destCanonical + File.separator
                    if (targetCanonical != destCanonical && !targetCanonical.startsWith(expectedPrefix)) {
                        cleanup(destDir)
                        return Result.failure(SkillZipError.PathTraversal(name))
                    }
                    if (entry.isDirectory) {
                        target.mkdirs()
                        zis.closeEntry()
                        continue
                    }
                    target.parentFile?.mkdirs()
                    target.outputStream().use { out ->
                        val buf = ByteArray(COPY_BUF)
                        while (true) {
                            val n = zis.read(buf)
                            if (n <= 0) break
                            totalBytes += n
                            if (totalBytes > MAX_UNCOMPRESSED_BYTES) {
                                cleanup(destDir)
                                return Result.failure(
                                    SkillZipError.TooLarge(
                                        "uncompressed size exceeds ${MAX_UNCOMPRESSED_BYTES / 1024 / 1024} MB"
                                    )
                                )
                            }
                            out.write(buf, 0, n)
                        }
                    }
                    zis.closeEntry()
                }
            }
        } catch (e: Throwable) {
            Log.w(TAG, "extractZipToDir: failed for ${destDir.absolutePath}", e)
            cleanup(destDir)
            return Result.failure(SkillZipError.IoError(e.message ?: "zip read failed"))
        }

        // Locate SKILL.md (case-insensitive). Prefer root, then a single nested subdir.
        val skillRoot = locateSkillRoot(destDir)
        if (skillRoot == null) {
            cleanup(destDir)
            return Result.failure(SkillZipError.MissingSkillMd)
        }
        return Result.success(skillRoot)
    }

    private fun locateSkillRoot(dir: File): File? {
        // 1. Root directly contains SKILL.md.
        if (dir.listFiles()?.any { it.isFile && it.name.equals("SKILL.md", ignoreCase = true) } == true) {
            return dir
        }
        // 2. Walk all subdirectories — return the first dir that contains SKILL.md. Most
        // GitHub-shaped zips nest exactly one level (`<repo-name>-<sha>/SKILL.md`), but
        // tolerate deeper nesting.
        dir.walkTopDown()
            .filter { it.isDirectory }
            .forEach { sub ->
                if (sub.listFiles()?.any { it.isFile && it.name.equals("SKILL.md", ignoreCase = true) } == true) {
                    return sub
                }
            }
        return null
    }

    private fun cleanup(dir: File) {
        runCatching { dir.deleteRecursively() }
    }
}

sealed class SkillZipError(message: String) : Exception(message) {
    object MissingSkillMd : SkillZipError("zip does not contain a SKILL.md file")
    class PathTraversal(val entryName: String) : SkillZipError("entry path is unsafe: $entryName")
    class TooLarge(reason: String) : SkillZipError(reason)
    class IoError(reason: String) : SkillZipError(reason)
}
