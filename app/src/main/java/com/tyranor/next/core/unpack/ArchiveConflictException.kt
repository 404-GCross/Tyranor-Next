package com.tyranor.next.core.unpack

import java.io.IOException

/** 同名产物冲突的类型（解压输出文件夹 / 封包输出文件 / 包内重名条目）。 */
enum class ArchiveConflictKind {
    /** 解压输出的同名文件夹已存在。 */
    OUTPUT_DIR_EXISTS,

    /** 封包输出的同名文件已存在。 */
    OUTPUT_FILE_EXISTS,

    /** 归档内存在重名条目（大小写折叠后）。 */
    DUPLICATE_ENTRY,
}

/**
 * 同名产物冲突（core 层类型化错误）：只携带 [kind] 与冲突主体 [subject]，
 * 不携带本地化文本——文案由 UI 层按 [kind] 格式化（AGENT.md core 错误协议）。
 */
class ArchiveConflictException(
    val kind: ArchiveConflictKind,
    val subject: String,
) : IOException("archive conflict: $kind: $subject")
