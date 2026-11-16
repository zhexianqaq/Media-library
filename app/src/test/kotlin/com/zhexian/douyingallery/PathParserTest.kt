package com.zhexian.douyingallery

import com.zhexian.douyingallery.data.scan.PathParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PathParser 单元测试（技术方案 §9「单元测试」清单）。
 * 纯 JVM，不依赖安卓环境。
 */
class PathParserTest {

    // ---------------------------------------------------------------- 编号文件

    @Test
    fun `普通编号文件`() {
        val parts = PathParser.parseName("角色-001.jpg")
        assertEquals("角色", parts.role)
        assertEquals(1, parts.seq)
        assertFalse(parts.isCaption)
        assertEquals("jpg", parts.ext)
    }

    @Test
    fun `四位序号`() {
        val parts = PathParser.parseName("角色-1000.mp4")
        assertEquals("角色", parts.role)
        assertEquals(1000, parts.seq)
        assertEquals("mp4", parts.ext)
    }

    @Test
    fun `角色名自带短横线`() {
        val parts = PathParser.parseName("A-B-003.jpg")
        assertEquals("A-B", parts.role)
        assertEquals(3, parts.seq)
    }

    @Test
    fun `角色名带多个短横线`() {
        val parts = PathParser.parseName("小美-日常-vlog-012.webp")
        assertEquals("小美-日常-vlog", parts.role)
        assertEquals(12, parts.seq)
    }

    // ---------------------------------------------------------------- 正文长图

    @Test
    fun `正文长图`() {
        val parts = PathParser.parseName("角色-正文.jpg")
        assertEquals("角色", parts.role)
        assertTrue(parts.isCaption)
        assertNull(parts.seq)
    }

    @Test
    fun `正文长图重名`() {
        val parts = PathParser.parseName("角色-正文-2.jpg")
        assertEquals("角色", parts.role)
        assertTrue(parts.isCaption)
        assertNull(parts.seq)
    }

    @Test
    fun `正文长图不能误判成编号文件`() {
        // 若先匹配编号规则，会把 role 解析成「角色-正文」、seq 解析成 2
        val parts = PathParser.parseName("A-正文-2.jpg")
        assertEquals("A", parts.role)
        assertTrue(parts.isCaption)
    }

    // ---------------------------------------------------------------- 不匹配

    @Test
    fun `不匹配的文件名仍然保留`() {
        val parts = PathParser.parseName("随手拍.jpg")
        assertNull(parts.role)
        assertNull(parts.seq)
        assertFalse(parts.isCaption)
        assertEquals("jpg", parts.ext)
    }

    @Test
    fun `两位数字不算序号`() {
        val parts = PathParser.parseName("角色-12.jpg")
        assertNull(parts.seq)
    }

    // ---------------------------------------------------------------- 忽略规则

    @Test
    fun `隐藏文件和 nomedia 被忽略`() {
        assertTrue(PathParser.isIgnored(".nomedia", 0))
        assertTrue(PathParser.isIgnored(".DS_Store", 10))
        assertTrue(PathParser.isIgnored(".trash", 0))
    }

    @Test
    fun `meta json 被忽略`() {
        assertTrue(PathParser.isIgnored("meta.json", 128))
    }

    @Test
    fun `零字节和临时文件被忽略`() {
        assertTrue(PathParser.isIgnored("角色-001.jpg", 0))
        assertTrue(PathParser.isIgnored("角色-001.jpg.tmp", 1024))
        assertTrue(PathParser.isIgnored("角色-001.mp4.part", 1024))
    }

    @Test
    fun `不支持的格式被忽略`() {
        assertTrue(PathParser.isIgnored("说明.txt", 10))
        assertTrue(PathParser.isIgnored("archive.zip", 10))
    }

    @Test
    fun `支持的图片和视频不被忽略`() {
        listOf("a.jpg", "b.JPEG", "c.png", "d.webp", "e.gif", "f.heic", "g.heif", "h.avif")
            .forEach { assertFalse("$it 应该被保留", PathParser.isIgnored(it, 100)) }
        listOf("a.mp4", "b.MOV", "c.m4v", "d.webm", "e.mkv", "f.3gp")
            .forEach { assertFalse("$it 应该被保留", PathParser.isIgnored(it, 100)) }
    }

    @Test
    fun `媒体类型判定`() {
        assertEquals(PathParser.TYPE_IMAGE.toLong(), PathParser.typeOf("jpg")!!.toLong())
        assertEquals(PathParser.TYPE_GIF.toLong(), PathParser.typeOf("gif")!!.toLong())
        assertEquals(PathParser.TYPE_VIDEO.toLong(), PathParser.typeOf("mp4")!!.toLong())
        assertNull(PathParser.typeOf("txt"))
    }

    // ---------------------------------------------------------------- 目录

    @Test
    fun `旧版目录记为旧版下载`() {
        assertEquals(PathParser.LEGACY_WORK_NAME, PathParser.workNameOf("视频"))
        assertEquals(PathParser.LEGACY_WORK_NAME, PathParser.workNameOf("图片"))
        assertEquals("某个作品", PathParser.workNameOf("某个作品"))
    }

    @Test
    fun `旧版结构完整解析`() {
        val entity = PathParser.buildMedia(
            path = "/storage/emulated/0/抖音解析/作者/视频/clip.mp4",
            name = "clip.mp4",
            size = 1024,
            mtime = 1_700_000_000_000,
            creator = "作者",
            workDirName = "视频",
            scanGen = 42
        )
        requireNotNull(entity)
        assertEquals("作者", entity.creator)
        assertEquals(PathParser.LEGACY_WORK_NAME, entity.work)
        assertEquals("/storage/emulated/0/抖音解析/作者/视频", entity.workPath)
        assertEquals(PathParser.TYPE_VIDEO.toLong(), entity.type.toLong())
        assertNull(entity.role)
        assertEquals(42L, entity.scanGen)
        assertEquals(1024L, entity.size)
    }

    @Test
    fun `新版结构完整解析`() {
        val entity = PathParser.buildMedia(
            path = "/storage/emulated/0/抖音解析/某UP/这是一个作品名字啊/角色-005.jpg",
            name = "角色-005.jpg",
            size = 2048,
            mtime = 1_700_000_000_000,
            creator = "某UP",
            workDirName = "这是一个作品名字啊",
            scanGen = 7
        )
        requireNotNull(entity)
        assertEquals("某UP", entity.creator)
        assertEquals("这是一个作品名字啊", entity.work)
        assertEquals("角色", entity.role)
        assertEquals(5, entity.seq)
        assertEquals(PathParser.TYPE_IMAGE.toLong(), entity.type.toLong())
    }

    @Test
    fun `被忽略的文件不产生实体`() {
        assertNull(
            PathParser.buildMedia(
                path = "/storage/emulated/0/抖音解析/UP/作品/.nomedia",
                name = ".nomedia",
                size = 0,
                mtime = 0,
                creator = "UP",
                workDirName = "作品",
                scanGen = 1
            )
        )
    }

    // ---------------------------------------------------------------- Unicode

    @Test
    fun `emoji 和生僻字按码点处理`() {
        val parts = PathParser.parseName("🐱可爱猫猫👀-007.jpg")
        assertEquals("🐱可爱猫猫👀", parts.role)
        assertEquals(7, parts.seq)

        val rare = PathParser.parseName("𠀀𠀁生僻字-正文.png")
        assertEquals("𠀀𠀁生僻字", rare.role)
        assertTrue(rare.isCaption)
        assertEquals(5, rare.role!!.codePointCount(0, rare.role.length))
    }

    @Test
    fun `大写扩展名统一小写`() {
        assertEquals("jpg", PathParser.extensionOf("A-001.JPG"))
        assertEquals(PathParser.TYPE_IMAGE.toLong(), PathParser.typeOf("JPG".lowercase())!!.toLong())
    }
}
