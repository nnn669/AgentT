package com.agentt.app.ui.terminal

import android.content.Context
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.json.JSONObject

data class AlpineManifest(
    val rootfsUrl: String,
    val rootfsSha256: String,
    val prootUrl: String,
    val prootSha256: String,
    val version: String
) {
    companion object {
        fun parse(json: String): AlpineManifest {
            val o = JSONObject(json)
            return AlpineManifest(
                o.getString("rootfs_url"), o.getString("rootfs_sha256"),
                o.getString("proot_url"), o.getString("proot_sha256"),
                o.getString("version")
            )
        }
    }
}

class AlpineRuntime(context: Context) {
    private val root = File(context.filesDir, "alpine").apply { mkdirs() }
    private val rootfs = File(root, "rootfs")
    private val proot = File(root, "proot")
    private val marker = File(root, "installed.version")

    fun isInstalled(version: String? = null): Boolean =
        rootfs.isDirectory && proot.canExecute() && (version == null || marker.readText() == version)

    fun install(manifest: AlpineManifest, onProgress: (Long, Long) -> Unit = { _, _ -> }) {
        val rootfsArchive = File(root, "rootfs.tar.gz")
        val prootFile = File(root, "proot.download")
        download(manifest.rootfsUrl, rootfsArchive, onProgress)
        verify(rootfsArchive, manifest.rootfsSha256)
        download(manifest.prootUrl, prootFile, onProgress)
        verify(prootFile, manifest.prootSha256)

        val staging = File(root, "rootfs.staging").apply { deleteRecursively(); mkdirs() }
        unpackTarGz(rootfsArchive, staging)
        val verifiedProot = File(root, "proot.verified")
        prootFile.copyTo(verifiedProot, overwrite = true)
        check(verifiedProot.setExecutable(true, false)) { "proot 无法设置为可执行" }
        rootfs.deleteRecursively()
        check(staging.renameTo(rootfs)) { "rootfs 安装失败" }
        proot.delete()
        check(verifiedProot.renameTo(proot)) { "proot 安装失败" }
        marker.writeText(manifest.version)
        rootfsArchive.delete()
        prootFile.delete()
    }

    fun command(command: String): List<String> {
        check(isInstalled()) { "Alpine 运行时尚未安装" }
        require(command.isNotBlank()) { "命令不能为空" }
        return listOf(
            proot.absolutePath, "-0", "-r", rootfs.absolutePath,
            "-b", "/dev", "-b", "/proc", "-b", "/sys",
            "/bin/sh", "-lc", command
        )
    }

    private fun download(url: String, target: File, onProgress: (Long, Long) -> Unit) {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 20_000
            connection.readTimeout = 60_000
            check(connection.responseCode in 200..299) { "下载失败 HTTP ${connection.responseCode}" }
            connection.inputStream.use { input ->
                val total = connection.contentLengthLong
                FileOutputStream(target).use { output ->
                    val buffer = ByteArray(32 * 1024)
                    var done = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        done += count
                        onProgress(done, total)
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun verify(file: File, expected: String) {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(32 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        check(actual.equals(expected.trim(), ignoreCase = true)) { "SHA-256 校验失败" }
    }

    private fun unpackTarGz(archive: File, destination: File) {
        GZIPInputStream(FileInputStream(archive)).use { gzip ->
            TarArchiveInputStream(gzip).use { tar ->
                while (true) {
                    val entry = tar.nextTarEntry ?: break
                    val target = File(destination, entry.name).canonicalFile
                    check(target.path.startsWith(destination.canonicalPath + File.separator)) { "rootfs 条目路径非法" }
                    if (entry.isDirectory) target.mkdirs()
                    else {
                        target.parentFile?.mkdirs()
                        FileOutputStream(target).use { out -> tar.copyTo(out) }
                        target.setExecutable((entry.mode and 0b001) != 0, false)
                    }
                }
            }
        }
    }
}