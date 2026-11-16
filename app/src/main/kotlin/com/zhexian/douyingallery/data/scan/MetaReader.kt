package com.zhexian.douyingallery.data.scan

import org.json.JSONObject
import java.io.File

/**
 * 可选增强：读取作品目录里的 meta.json（需要「抖音解析」APK 配合写入，第三期功能）。
 * 读不到 / 解析失败 / 脏数据一律返回 null，不影响主流程。
 */
object MetaReader {

    data class MetaInfo(
        val source: String?,
        val author: String?,
        val desc: String?,
        val tags: List<String>,
        val url: String?,
        val downloadedAt: Long
    )

    fun read(workDir: String): MetaInfo? {
        val file = File(workDir, PathParser.META_FILE)
        if (!file.isFile || file.length() <= 0L) return null
        return runCatching {
            val json = JSONObject(file.readText())
            val tags = ArrayList<String>()
            json.optJSONArray("tags")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val t = arr.optString(i, "")
                    if (t.isNotBlank()) tags.add(t.trim())
                }
            }
            MetaInfo(
                source = json.optString("source").takeIf { it.isNotBlank() },
                author = json.optString("author").takeIf { it.isNotBlank() },
                desc = json.optString("desc").takeIf { it.isNotBlank() },
                tags = tags,
                url = json.optString("url").takeIf { it.isNotBlank() },
                downloadedAt = json.optLong("downloadedAt", 0L)
            )
        }.getOrNull()
    }
}
