package com.tyranor.next.core.game.scan

import com.tyranor.next.core.engine.EngineType
import com.tyranor.next.core.game.model.ScanGame

/**
 * System4 引入后的一次性定点重检测（迁移）的纯函数部分，可 JVM 单测。
 *
 * 旧版本把 System4 游戏存成了 XSYS35，增量扫描对已入库目录剪枝，不会改判。
 * 迁移只针对库中 XSYS35 条目重跑识别（[EngineScanner.redetectXs35Games]），不做整库重扫：
 * 整库重扫会重置标题、手动改过的引擎与启动文件，并在扫描根不可读时删除游戏。
 */
object Xs4RescanMigration {

    /** 库中需要定点重检测的条目（engine == XSYS35）。 */
    fun targetsOf(games: Iterable<ScanGame>): List<ScanGame> =
        games.filter { it.engine == EngineType.XSYS35 }

    /**
     * 对单条游戏应用定点重检测结果。
     *
     * - 仅当 [game] 为 XSYS35 且 [detection] 为 XSYS4 时改判：更新 engine、launchTarget；
     * - 标题仅当仍等于目录名（未被用户改名）且 [EngineScanner.Detection.titleHint] 非空时替换为 GameName；
     * - 其余字段一律不动；其它情况（真正的 XSYS35、识别结果非 XSYS4、读取失败 [detection] 为 null）
     *   原样返回同一实例，调用方据此计数。
     */
    fun applyDetection(game: ScanGame, detection: EngineScanner.Detection?, dirName: String): ScanGame {
        if (game.engine != EngineType.XSYS35) return game
        if (detection == null || detection.engine != EngineType.XSYS4) return game
        val hint = detection.titleHint
        val title = if (hint != null && game.title == dirName) hint else game.title
        return game.copy(engine = EngineType.XSYS4, launchTarget = detection.launchTarget, title = title)
    }

    /**
     * 所有目标是否都已得到确定结果，可置位迁移标志（无目标时即为真）。
     * 任一条目为 [Xs4RedetectOutcome.Missing] 或 [Xs4RedetectOutcome.Unreadable] 时为假，下次启动重试。
     */
    fun canMarkDone(outcomes: Iterable<Xs4RedetectOutcome>): Boolean =
        outcomes.all { it is Xs4RedetectOutcome.Done }
}

/** 定点重检测对单个 XSYS35 条目的结果（[EngineScanner.redetectXs35Games] 产出）。 */
sealed interface Xs4RedetectOutcome {

    /** 已完成识别；[detection] 为 XSYS4 时改判，否则原样保留。 */
    data class Done(val detection: EngineScanner.Detection, val dirName: String) : Xs4RedetectOutcome

    /**
     * 目录或其所在授权根暂时不可达（未挂载、SAF 根未授权、节点查不到），无法判断；
     * 与「目录真被删除」无法区分，因此同样需要下次启动重试。
     */
    data object Missing : Xs4RedetectOutcome

    /** 目录存在但读取失败，原样保留并需下次启动重试。 */
    data object Unreadable : Xs4RedetectOutcome
}

/**
 * 定点重检测汇总：[changed] 为改判条数；[complete] 为真表示所有目标都已确定结果（或无目标），
 * 调用方据此置位迁移标志；为假时标志保持未置位，下次启动仅重试仍为 XSYS35 的条目。
 */
data class Xs4RedetectReport(val changed: Int, val complete: Boolean)
