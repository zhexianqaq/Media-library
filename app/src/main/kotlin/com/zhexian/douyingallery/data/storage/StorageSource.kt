package com.zhexian.douyingallery.data.storage

import com.zhexian.douyingallery.data.scan.PathParser
import java.io.File

/**
 * 存储访问抽象。第一/二期只有 [FileSource]（依赖「所有文件访问」权限）；
 * 第三期若要用 SAF，只需要再加一个 [SafSource] 实现，上层不动。
 */
interface StorageSource {
    fun isRootAvailable(root: String): Boolean
    fun listDirs(parent: String): List<String>
    fun listFiles(dir: String): List<Entry>
    fun lastModified(path: String): Long
    fun exists(path: String): Boolean
    fun length(path: String): Long

    data class Entry(val path: String, val name: String, val size: Long, val mtime: Long, val isDirectory: Boolean)
}

/** 直接用 java.io.File 访问（Android 11+ 需要 MANAGE_EXTERNAL_STORAGE） */
object FileSource : StorageSource {

    override fun isRootAvailable(root: String): Boolean = File(root).isDirectory

    override fun listDirs(parent: String): List<String> {
        val dir = File(parent)
        val children = dir.listFiles() ?: return emptyList()
        return children.asSequence()
            .filter { it.isDirectory && !it.name.startsWith(".") }
            .map { it.absolutePath }
            .sorted()
            .toList()
    }

    override fun listFiles(dir: String): List<StorageSource.Entry> {
        val children = File(dir).listFiles() ?: return emptyList()
        val out = ArrayList<StorageSource.Entry>(children.size)
        for (f in children) {
            val name = f.name
            if (name.startsWith(".")) continue
            if (!f.isFile) continue
            val size = f.length()
            if (size <= 0L) continue
            if (!PathParser.isMediaName(name)) continue
            out.add(StorageSource.Entry(f.absolutePath, name, size, f.lastModified(), false))
        }
        return out
    }

    override fun lastModified(path: String): Long = File(path).lastModified()

    override fun exists(path: String): Boolean = File(path).exists()

    override fun length(path: String): Long = File(path).length()
}
