package com.agentt.app.ui.files

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FileTools {
    private const val MAX_READ_CHARS = 50_000
    private const val MAX_LIST_ITEMS = 200

    /** 获取文件工具可访问的根目录 */
    fun getBaseDir(context: Context): File {
        // 优先使用外部存储目录，回退到内部 files 目录
        return context.getExternalFilesDir(null)?.parentFile
            ?: context.filesDir.parentFile
            ?: File("/storage/emulated/0")
    }

    /** 安全解析路径，防止路径穿越 */
    fun resolvePath(baseDir: File, path: String): File? {
        val clean = path.trimStart('/').trim()
        if (clean.isBlank()) return baseDir
        val target = File(baseDir, clean).normalize().absoluteFile
        // 防止路径穿越到 baseDir 之外
        return if (target.absolutePath.startsWith(baseDir.absolutePath)) target else null
    }

    /** 文件列表 */
    suspend fun list(baseDir: File, path: String): String = withContext(Dispatchers.IO) {
        val dir = resolvePath(baseDir, path) ?: return@withContext "路径无效或越界"
        if (!dir.isDirectory) return@withContext "不是目录"
        val files = dir.listFiles()?.sortedWith(compareBy<File> { if (it.isDirectory) 0 else 1 }.thenBy { it.name.lowercase() })
            ?: return@withContext "无法读取目录"
        val sb = StringBuilder()
        sb.appendLine("目录: ${dir.absolutePath}")
        sb.appendLine("总数: ${files.size} 项")
        sb.appendLine()
        files.take(MAX_LIST_ITEMS).forEach { f ->
            val type = if (f.isDirectory) "[DIR]" else "[FILE]"
            val size = if (f.isFile) formatSize(f.length()) else "-"
            val mod = formatTime(f.lastModified())
            sb.appendLine("$type $size $mod ${f.name}")
        }
        if (files.size > MAX_LIST_ITEMS) {
            sb.appendLine("... 还有 ${files.size - MAX_LIST_ITEMS} 项未显示")
        }
        sb.toString().trim()
    }

    /** 读取文本文件 */
    suspend fun read(baseDir: File, path: String): String = withContext(Dispatchers.IO) {
        val file = resolvePath(baseDir, path) ?: return@withContext "路径无效或越界"
        if (!file.exists()) return@withContext "文件不存在: ${file.absolutePath}"
        if (!file.isFile) return@withContext "不是文件"
        if (!file.canRead()) return@withContext "文件不可读"
        val size = file.length()
        val text = file.readText(charset = Charsets.UTF_8).take(MAX_READ_CHARS)
        val truncated = if (text.length >= MAX_READ_CHARS) "\n\n...(文件过长，仅显示前 $MAX_READ_CHARS 字符)" else ""
        buildString {
            appendLine("文件: ${file.absolutePath}")
            appendLine("大小: ${formatSize(size)}")
            appendLine("修改时间: ${formatTime(file.lastModified())}")
            appendLine()
            append(text)
            append(truncated)
        }.trim()
    }

    /** 写入文本文件 */
    suspend fun write(baseDir: File, path: String, content: String): String = withContext(Dispatchers.IO) {
        val file = resolvePath(baseDir, path) ?: return@withContext "路径无效或越界"
        try {
            file.parentFile?.mkdirs()
            file.writeText(content, Charsets.UTF_8)
            "已写入 ${file.absolutePath} (${formatSize(file.length())})"
        } catch (e: Exception) {
            "写入失败: ${e.message ?: e.javaClass.simpleName}"
        }
    }

    /** 文件元信息 */
    suspend fun stat(baseDir: File, path: String): String = withContext(Dispatchers.IO) {
        val file = resolvePath(baseDir, path) ?: return@withContext "路径无效或越界"
        if (!file.exists()) return@withContext "文件不存在"
        buildString {
            appendLine("路径: ${file.absolutePath}")
            appendLine("名称: ${file.name}")
            appendLine("类型: ${if (file.isDirectory) "目录" else "文件"}")
            if (file.isFile) {
                appendLine("大小: ${formatSize(file.length())}")
            }
            appendLine("修改时间: ${formatTime(file.lastModified())}")
            appendLine("可读: ${if (file.canRead()) "是" else "否"}")
            appendLine("可写: ${if (file.canWrite()) "是" else "否"}")
        }.trim()
    }

    /** 删除文件 */
    suspend fun delete(baseDir: File, path: String): String = withContext(Dispatchers.IO) {
        val file = resolvePath(baseDir, path) ?: return@withContext "路径无效或越界"
        if (!file.exists()) return@withContext "文件不存在"
        try {
            if (file.isDirectory) {
                file.deleteRecursively()
                "已删除目录: ${file.absolutePath}"
            } else {
                file.delete()
                "已删除文件: ${file.absolutePath}"
            }
        } catch (e: Exception) {
            "删除失败: ${e.message ?: e.javaClass.simpleName}"
        }
    }

    private fun formatSize(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> String.format("%.1f KB", bytes / 1024.0)
        bytes < 1024 * 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
        else -> String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0))
    }

    private fun formatTime(millis: Long): String {
        if (millis <= 0) return "-"
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        return sdf.format(Date(millis))
    }
}
