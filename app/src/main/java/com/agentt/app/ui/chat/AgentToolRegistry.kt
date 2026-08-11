package com.agentt.app.ui.chat

import org.json.JSONObject

data class AgentToolSpec(val id: String, val description: String, val arguments: String)

data class AgentRequirement(
    val kind: String,
    val key: String = "",
    val title: String = "需要你的操作",
    val reason: String = "完成后 AgentT 会继续当前任务。"
) {
    fun encode(): String = JSONObject().put("kind", kind).put("key", key).put("title", title).put("reason", reason).toString()
    companion object {
        fun decode(value: String): AgentRequirement? = runCatching {
            val o = JSONObject(value)
            AgentRequirement(o.optString("kind"), o.optString("key"), o.optString("title").ifBlank { "需要你的操作" }, o.optString("reason").ifBlank { "完成后 AgentT 会继续当前任务。" })
        }.getOrNull()
    }
}

object AgentToolRegistry {
    private const val MARKER = "[[AGENT_REQUIREMENT]]"
    val tools = listOf(
        AgentToolSpec("browser.search", "搜索互联网并返回结果", "query:string"),
        AgentToolSpec("browser.extract", "读取网页正文", "url:string"),
        AgentToolSpec("browser.title", "读取网页标题", "url:string"),
        AgentToolSpec("browser.links", "提取网页链接", "url:string"),
        AgentToolSpec("browser.open", "在应用内浏览器打开网页", "url:string"),
        AgentToolSpec("terminal.exec", "在 AgentT 私有沙盒执行命令，应用会自动尝试恢复缺少的软件包", "command:string, backend?:LOCAL, timeout_ms?:1000..120000"),
        AgentToolSpec("file.read", "读取文本文件内容", "path:string"),
        AgentToolSpec("file.write", "写入文本内容到文件", "path:string, content:string"),
        AgentToolSpec("file.list", "列出目录中的文件和子目录", "path:string"),
        AgentToolSpec("file.stat", "获取文件或目录的元信息", "path:string"),
        AgentToolSpec("file.delete", "删除文件或目录", "path:string")
    )

    fun systemPrompt(): String = buildString {
        appendLine("你是 AgentT 自主智能体，运行在安卓手机上。用户只描述目标，不需要指定工具。")
        appendLine("你必须自行理解目标、规划步骤、从当前工具目录选择能力、执行、检查结果，并持续推进到完成。")
        appendLine("当前应用实际可用工具：")
        tools.forEach { appendLine("- ${it.id}(${it.arguments})：${it.description}") }
        appendLine("只能输出 JSON 动作流：{\"actions\":[{\"type\":\"think\",\"content\":\"下一步\"},{\"type\":\"tool\",\"tool\":\"browser.search\",\"query\":\"...\"},{\"type\":\"reply\",\"content\":\"最终结果\"}]}")
        appendLine("工具结果会自动回传；未完成时继续。只有必须由用户提供供应商、密钥、权限或输入时才输出 require。requirement 只能是 provider、secret、permission、runtime、input。")
        appendLine("require 示例：{\"actions\":[{\"type\":\"require\",\"requirement\":\"secret\",\"key\":\"GITHUB_TOKEN\",\"title\":\"需要 GitHub Token\",\"reason\":\"用于访问私有仓库\"}]}")
        appendLine("reply 必须基于真实结果，使用与用户相同的语言。")
    }.trim()

    fun canonicalId(id: String): String = when (id.lowercase()) {
        "search", "extract", "title", "links", "open" -> "browser.${id.lowercase()}"
        "terminal" -> "terminal.exec"
        "read", "write", "list", "stat", "delete" -> "file.${id.lowercase()}"
        else -> id.lowercase()
    }

    fun actionLabel(id: String): String = when (canonicalId(id)) {
        "browser.search" -> "搜索网页"
        "browser.extract" -> "读取网页正文"
        "browser.title" -> "获取网页标题"
        "browser.links" -> "提取网页链接"
        "browser.open" -> "打开网页"
        "terminal.exec" -> "执行终端任务"
        "file.read" -> "读取文件"
        "file.write" -> "写入文件"
        "file.list" -> "列出目录"
        "file.stat" -> "查看文件信息"
        "file.delete" -> "删除文件"
        else -> "执行工具"
    }

    fun requirementMarker(value: AgentRequirement): String = MARKER + value.encode()
    fun requirementFromToolResult(value: String): AgentRequirement? = value.substringAfter(MARKER, "").lineSequence().firstOrNull()?.let { AgentRequirement.decode(it) }
}

object AgentPackageRecovery {
    private val packages = mapOf("git" to "git", "curl" to "curl", "wget" to "wget", "jq" to "jq", "python" to "python3", "python3" to "python3", "node" to "nodejs", "npm" to "npm", "ffmpeg" to "ffmpeg", "rg" to "ripgrep")
    fun missingCommand(output: String): String? {
        val a = Regex("(?im)(?:^|[\\s:])([a-zA-Z0-9._+-]+): (?:not found|inaccessible)").find(output)?.groupValues?.getOrNull(1)
        val b = Regex("(?im)command not found:?\\s*([a-zA-Z0-9._+-]+)").find(output)?.groupValues?.getOrNull(1)
        return listOfNotNull(a, b).firstOrNull { packages.containsKey(it) }
    }
    fun installCommand(manager: String, command: String): String? {
        val pkg = packages[command] ?: return null
        return when (manager.substringAfterLast('/')) {
            "apk" -> "apk add --no-cache $pkg"
            "pkg" -> "pkg install -y $pkg"
            "apt-get" -> "apt-get update && apt-get install -y $pkg"
            else -> null
        }
    }
    fun runtimeRequirement(command: String) = AgentRequirement("runtime", command, "需要可安装工具的终端环境", "任务需要命令 $command，但当前 Android 沙盒没有可用包管理器。初始化终端环境后，AgentT 会继续任务。")
}