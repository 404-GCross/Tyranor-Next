package com.tyranor.next.core.game.launch

/**
 * System4 启动前预检（纯函数，App 侧）。
 *
 * 引擎遇错会 `exit()` 整个 `:xsys4` 进程（G13），Activity 在 `super.onCreate` 前 finish 还会触发
 * `SuperNotCalledException`，因此可预见的错误必须在拉起宿主之前以类型化失败拦下。
 * 入口 ini 的查找由调用方经 `Xs4Paths.resolveIniFile` 完成，本类只做判定，便于单测。
 */
object Xs4Preflight {

    /** `ConfigurationInfo.reqGlEsVersion` 中 OpenGL ES 3.0 的编码（主版本 << 16 | 次版本）。 */
    const val GLES3_VERSION = 0x30000

    /**
     * [reqGlEsVersion] 为 null（系统未提供 ConfigurationInfo）或 0（`ro.opengles.version` 未设置，
     * 即系统没有报告版本，并不代表只支持 GLES 1）时放行，避免误拦能跑的机型；
     * 明确低于 3.0 的才拦下。engine 侧 `Xs4Activity` 的防御性预检需保持同一口径。
     */
    fun hasGles3(reqGlEsVersion: Int?): Boolean =
        reqGlEsVersion == null || reqGlEsVersion <= 0 || reqGlEsVersion >= GLES3_VERSION

    /** 入口 ini 缺失优先于 GLES 判定；全部满足返回 null。 */
    fun check(iniFound: Boolean, reqGlEsVersion: Int?): LaunchResult.Failure? = when {
        !iniFound -> LaunchResult.Failure.Xs4IniMissing
        !hasGles3(reqGlEsVersion) -> LaunchResult.Failure.Xs4Gles3Required
        else -> null
    }
}
