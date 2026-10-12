package com.tyranor.next.core.engine.external

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * RTP 导入的替换语义回归（备份式替换 + 中断恢复）。
 *
 * 锁定的都是**用户数据安全**语义：替换失败不得让用户丢掉已导入的 RTP。
 * 先前实现是「先删旧目录再铺新内容」，`copyRecursively` 中途失败会留下半成品目录，
 * 而 [RpgMakerRuntimeEnvironment.isRtpImported] 只看目录非空 → 下次显示「已导入」却缺素材。
 */
class RtpImportReplaceSemanticsTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private fun buildZip(entries: List<Pair<String, ByteArray>>): ByteArray {
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return bos.toByteArray()
    }

    @Test
    fun interruptedReplaceLeavesBackupOnDiskAndRecoveryRestoresIt() {
        // 模拟「旧目录已改名成备份、新内容尚未顶上」时被杀：盘上应仍保有完整旧数据，
        // 且恢复逻辑会把它归位（而不是报「未导入」）。
        // 注意：此前该测试只断言备份文件还在、从未执行恢复路径；这里真正调用
        // recoverPendingImport 验证归位（依赖 File，可在 JVM 上跑）。
        val parent = temporaryFolder.newFolder("parent")
        val target = File(parent, "app")
        val backup = File(parent, ".import_backup")
        backup.mkdirs()
        File(backup, "keep.txt").writeText("old-content")

        assertFalse("目标缺失时恢复前应显示未导入", target.exists())
        assertTrue("备份必须还在盘上（数据未丢）", File(backup, "keep.txt").isFile)

        assertTrue("恢复必须成功", RpgMakerRuntimeEnvironment.recoverPendingImport(target))
        assertTrue("旧数据必须归位到 app 目录", File(target, "keep.txt").isFile)
        assertEquals("old-content", File(target, "keep.txt").readText())
        assertFalse("归位后备份目录应被消费", backup.exists())
    }

    @Test
    fun recoveryRestoresEmptyTargetWithBackup() {
        // 目标为「空目录」+ 备份完整：空目标只可能是上次替换在 copyRecursively 刚建目录
        // 就被杀留下的半成品，备份才是完整旧数据（isRtpImported 只看目录非空，不归位会误报）
        val parent = temporaryFolder.newFolder("empty-target")
        val target = File(parent, "app").apply { mkdirs() }
        val backup = File(parent, ".import_backup").apply { mkdirs() }
        File(backup, "gfx.png").writeText("old-rtp")

        assertTrue(RpgMakerRuntimeEnvironment.recoverPendingImport(target))
        assertEquals("old-rtp", File(target, "gfx.png").readText())
        assertFalse(backup.exists())
    }

    @Test
    fun recoveryRestoresBackupWhenMarkerAndPartialTargetPresent() {
        // 提交标记 + 备份 + 半成品 target（copyRecursively 只拷了一半就被杀）：
        // 必须删掉半成品、放回备份、清掉标记——否则 isRtpImported 会报「已导入」却缺素材
        val parent = temporaryFolder.newFolder("marker-partial")
        val target = File(parent, "app").apply { mkdirs() }
        File(target, "half.bin").writeText("partial-copy")
        val backup = File(parent, ".import_backup").apply { mkdirs() }
        File(backup, "full.bin").writeText("complete-old")
        File(parent, ".importing").writeText("")

        assertTrue(RpgMakerRuntimeEnvironment.recoverPendingImport(target))
        assertFalse("半成品必须清除", File(target, "half.bin").exists())
        assertEquals("complete-old", File(target, "full.bin").readText())
        assertFalse("备份已消费", backup.exists())
        assertFalse("标记已清除", File(parent, ".importing").exists())
    }

    @Test
    fun recoveryClearsFirstImportPartialWhenNoBackup() {
        // 首次导入中断：标记 + 半成品 + 无备份 → 半成品无旧数据可保护，整体清除（连同标记）
        val parent = temporaryFolder.newFolder("marker-first")
        val target = File(parent, "app").apply { mkdirs() }
        File(target, "half.bin").writeText("partial-copy")
        File(parent, ".importing").writeText("")

        assertTrue(RpgMakerRuntimeEnvironment.recoverPendingImport(target))
        assertFalse("首次导入的半成品应被清除", target.exists())
        assertFalse("标记应被清除", File(parent, ".importing").exists())
    }

    @Test
    fun commitImportTempSuccessInstallsAndCleansMarkers() {
        // 提交段成功：新内容顶上、提交标记与备份都清掉（盘上不留「待恢复」现场）
        val parent = temporaryFolder.newFolder("commit")
        val temp = File(parent, ".import_tmp").apply { mkdirs() }
        File(temp, "gfx.png").writeText("new-rtp")
        val target = File(parent, "app").apply { mkdirs() }
        File(target, "old.bin").writeText("old-rtp")

        val rejection = RpgMakerRuntimeEnvironment.commitImportTemp(temp, target)
        assertEquals("成功不应返回拒绝原因", null, rejection)
        assertEquals("new-rtp", File(target, "gfx.png").readText())
        assertFalse("旧数据应被新内容替换", File(target, "old.bin").exists())
        assertFalse("备份应清掉", File(parent, ".import_backup").exists())
        assertFalse("提交标记应清掉", File(parent, ".importing").exists())
    }

    @Test
    fun emptyArchiveDoesNotDestroyExistingTarget() {
        // 空来源必须被拦在替换之前：旧实现会先删掉 target 再发现内容为空，
        // 用户的既有 RTP 就此消失。
        // 此前该测试把「既有 RTP」放在一个目录、却对另一个 staging 目录解压，
        // existing 完全不在调用路径上、断言恒真；这里直接走提交段验证。
        val parent = temporaryFolder.newFolder("empty")
        val temp = File(parent, ".import_tmp").apply { mkdirs() }
        val existing = File(parent, "app").apply { mkdirs() }
        File(existing, "gfx.png").writeText("user-rtp")

        val rejection = RpgMakerRuntimeEnvironment.commitImportTemp(temp, existing)

        assertEquals(RtpImportRejection.EmptyArchive, rejection)
        assertTrue("既有内容不得被空来源清掉", File(existing, "gfx.png").isFile)
        assertEquals("user-rtp", File(existing, "gfx.png").readText())
        assertFalse("空来源不得留下备份/标记现场", File(parent, ".import_backup").exists())
        assertFalse(File(parent, ".importing").exists())
    }

    @Test
    fun recoveryKeepsStateWhenPartialCannotBeDeleted() {
        // 注入删除失败：半成品 target 删不掉时，恢复必须**中止**（返回 false），
        // 保留「标记 + 备份」现场待重试——若继续删标记声称成功，下次恢复会把
        // 「非空 target + 备份」判成「已成功替换的残留」而删掉唯一旧数据。
        val parent = temporaryFolder.newFolder("delete-fail")
        val target = File(parent, "app").apply { mkdirs() }
        File(target, "half.bin").writeText("partial-copy")
        val backup = File(parent, ".import_backup").apply { mkdirs() }
        File(backup, "full.bin").writeText("complete-old")
        File(parent, ".importing").writeText("")

        val recovered = RpgMakerRuntimeEnvironment.recoverPendingImport(target) { false }

        assertFalse("删除失败时恢复必须中止", recovered)
        assertTrue("标记必须保留（下次恢复重试的依据）", File(parent, ".importing").exists())
        assertTrue("备份必须保留", File(backup, "full.bin").isFile)
        assertEquals("complete-old", File(backup, "full.bin").readText())
        assertTrue("半成品 target 不得被声称清除", File(target, "half.bin").isFile)
    }

    @Test
    fun recoveryKeepsStateWhenStaleBackupCannotBeDeleted() {
        // 目标有效 + 陈旧备份删不掉（成功路径清理失败留下的孤儿占住 .import_backup）：
        // 恢复必须中止而非继续——继续会让后续 target.renameTo(backup) 失败，被迫走
        // 「删旧数据而不备份」的危险分支
        val parent = temporaryFolder.newFolder("stale-delete-fail")
        val target = File(parent, "app").apply { mkdirs() }
        File(target, "current.bin").writeText("current")
        val backup = File(parent, ".import_backup").apply { mkdirs() }
        File(backup, "old.bin").writeText("old")

        val recovered = RpgMakerRuntimeEnvironment.recoverPendingImport(target) { false }

        assertFalse("陈旧备份删不掉时恢复必须中止", recovered)
        assertEquals("current", File(target, "current.bin").readText())
        assertTrue("备份仍保留", File(backup, "old.bin").isFile)
    }

    @Test
    fun commitAbortsWithoutDataLossWhenStaleBackupCannotBeDeleted() {
        // 与上一条同场景但走提交段入口：陈旧备份删不掉 → 本次导入必须放弃（IoFailed），
        // 旧数据（target）原样保留
        val parent = temporaryFolder.newFolder("commit-stale-fail")
        val temp = File(parent, ".import_tmp").apply { mkdirs() }
        File(temp, "new.bin").writeText("new-rtp")
        val target = File(parent, "app").apply { mkdirs() }
        File(target, "current.bin").writeText("current")
        val backup = File(parent, ".import_backup").apply { mkdirs() }
        File(backup, "old.bin").writeText("old")

        val rejection = RpgMakerRuntimeEnvironment.commitImportTemp(temp, target) { false }

        assertTrue("陈旧备份占位时必须放弃导入", rejection is RtpImportRejection.IoFailed)
        assertEquals("current", File(target, "current.bin").readText())
        assertTrue("旧数据仍在", File(backup, "old.bin").isFile)
        assertFalse("导入未完成，不得留下提交标记", File(parent, ".importing").exists())
    }

    @Test
    fun successfulExtractionReportsNoRejection() {
        val dest = temporaryFolder.newFolder("ok")
        val rejection = RpgMakerRuntimeEnvironment.extractZipStream(
            ByteArrayInputStream(buildZip(listOf("app/data.txt" to "hello".toByteArray()))),
            dest,
        )
        assertEquals(null, rejection)
        assertEquals("hello", File(dest, "app/data.txt").readText())
    }

    @Test
    fun oversizeEntryReportsRejectionAndLeavesNoPartialFile() {
        val dest = temporaryFolder.newFolder("oversize")
        val rejection = RpgMakerRuntimeEnvironment.extractZipStream(
            ByteArrayInputStream(buildZip(listOf("app/big.bin" to ByteArray(4 * 1024 * 1024)))),
            dest,
            maxEntryBytes = 1024 * 1024,
        )
        assertNotNull("超限必须上报类型化原因", rejection)
        assertTrue(rejection is RtpImportRejection.EntrySizeExceeded)
        assertFalse("超限条目不得留下截断文件", File(dest, "app/big.bin").exists())
    }
}
