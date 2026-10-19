package com.tyranor.next.ui.common

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.tyranor.next.theme.AppThemeColors
import top.yukonga.miuix.kmp.basic.Scaffold as MiuixScaffold

/** 顶栏渐进模糊的最大模糊半径（顶端）。 */
private val TopBarBlurRadius: Dp = 44.dp

/** 顶栏底色叠层的最大透明度（顶端值，随整体遮罩向下渐隐到 0）。 */
private const val TopBarTintAlpha = 0.6f

/** 底色叠层开始变淡的位置（占顶栏高度比例）：之前保持最大透明度，之后平滑收敛到顶栏底边的 0。 */
private const val TopBarTintFadeStart = 0.2f

/** 整体遮罩保持不透明的范围占顶栏高度的比例：此范围内完全不透明，之后一路渐隐到顶栏底边（不超出顶栏）。 */
private const val TopBarOpaqueBarFraction = 5f / 6f

private const val TopBarMaskSteps = 32

/** 渐进模糊的叠层数：半径按 1/n … n/n 等分，自上而下逐层淡出，使模糊强度随位置连续递减。 */
private const val TopBarBlurLayerCount = 6

/**
 * 每层淡出区间的宽度（占顶栏高度比例）：各层区间起点在 0..(1−span) 内等距错开、彼此大幅重叠，
 * 相邻两层的过渡叠在一起，强度变化连续，不会出现「一档一档」的台阶。
 */
private const val TopBarLayerFadeSpan = 0.7f

/**
 * 竖向遮罩的多段采样：从 [start] 起到 [end] 止 alpha = 1 − smootherstep（6x⁵−15x⁴+10x³，
 * 两端一阶、二阶导数均为 0），起点与终点都无折角，比少量线性分段平滑得多。
 */
private fun fadeStops(start: Float, end: Float): Array<Pair<Float, Color>> =
    Array(TopBarMaskSteps + 1) { i ->
        val t = i / TopBarMaskSteps.toFloat()
        val x = ((t - start) / (end - start)).coerceIn(0f, 1f)
        val eased = x * x * x * (x * (x * 6f - 15f) + 10f)
        t to Color.Black.copy(alpha = 1f - eased)
    }

/** 各模糊层的淡出遮罩，下标 0 为半径最小的一层（最先淡出）。 */
private val TopBarLayerMaskStops: List<Array<Pair<Float, Color>>> = List(TopBarBlurLayerCount) { index ->
    val step = (1f - TopBarLayerFadeSpan) / (TopBarBlurLayerCount - 1)
    val start = (TopBarBlurLayerCount - 1 - index) * step
    fadeStops(start, start + TopBarLayerFadeSpan)
}

private enum class TopBarBlurSlot { TopBar, Blur, Content }

/**
 * 顶栏渐进模糊是否生效：仅**默认外观风格**且 Android 12+（模糊依赖 RenderEffect）。
 * 玻璃系外观风格（复古 / 高级）不参与，保持顶栏纯透明。
 */
@Composable
fun isTopBarBlurActive(): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
        !AppThemeColors.isGlass &&
        !AppThemeColors.isAdvancedGlass

/**
 * 顶栏 + 正文的统一宿主：默认外观风格下正文铺满整页并垫在顶栏下方，
 * 仅在顶栏自身范围内叠一层渐进模糊：顶端最糊最实，向下模糊与不透明度连续递减，
 * 到顶栏底边完全清晰透明（不超出顶栏）；其余情况退化为「顶栏在上、正文在下」的 Column。
 * 层级自下而上为：正文 → 模糊层 → 顶栏（标题 / 图标始终清晰），模糊层不拦截触摸。
 *
 * [content] 收到的 `topInset` 为顶栏高度（退化时为 0）：滚动列表须把它加到 `contentPadding.top`，
 * 非滚动内容用 `padding(top = topInset)`，这样静止时内容不被顶栏遮挡，滚动时才从模糊区下穿过。
 */
@Composable
fun TopBarBlurHost(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit,
    content: @Composable (topInset: Dp) -> Unit,
) {
    if (!isTopBarBlurActive()) {
        Column(modifier.fillMaxSize()) {
            topBar()
            content(0.dp)
        }
        return
    }
    val backdrop = rememberLayerBackdrop()
    SubcomposeLayout(modifier.fillMaxSize()) { constraints ->
        val topBarPlaceables = subcompose(TopBarBlurSlot.TopBar, topBar)
            .map { it.measure(constraints.copy(minWidth = constraints.maxWidth, minHeight = 0)) }
        val topInsetPx = topBarPlaceables.maxOfOrNull { it.height } ?: 0
        val topInset = topInsetPx.toDp()
        // 模糊层严格等于顶栏高度，不向正文区延伸
        val blurPlaceables = subcompose(TopBarBlurSlot.Blur) {
            TopBarBlurLayer(backdrop = backdrop, barHeightPx = topInsetPx)
        }.map { it.measure(Constraints.fixed(constraints.maxWidth, topInsetPx)) }
        val contentPlaceables = subcompose(TopBarBlurSlot.Content) {
            Box(Modifier.fillMaxSize().layerBackdrop(backdrop)) { content(topInset) }
        }.map { it.measure(constraints) }
        layout(constraints.maxWidth, constraints.maxHeight) {
            contentPlaceables.forEach { it.place(0, 0) }
            blurPlaceables.forEach { it.place(0, 0) }
            topBarPlaceables.forEach { it.place(0, 0) }
        }
    }
}

/**
 * Miuix 设置类页面的 [TopBarBlurHost] 封装：外层保留 Miuix `Scaffold`（弹窗 / 下拉等 overlay 组件依赖它承载，
 * 容器透明、不吃系统 inset），顶栏与正文交由 [TopBarBlurHost]。调用方须在 `MiuixSettingsTheme` 内使用。
 */
@Composable
fun MiuixTopBarBlurScaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit,
    content: @Composable (topInset: Dp) -> Unit,
) {
    MiuixScaffold(
        modifier = modifier,
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0.dp),
    ) { _ ->
        TopBarBlurHost(topBar = topBar, content = content)
    }
}

/**
 * 渐进模糊层：[TopBarBlurLayerCount] 层半径递增的 backdrop 模糊自下而上叠放，
 * 每层按各自的淡出遮罩渐隐，模糊强度因此随位置连续变化（顶端最强、向下逐渐清晰），
 * 而不是「均匀模糊 + 透明度渐变」那样在窄带内从糊到清晰地突变。
 * 页面底色半透明叠层只画一次，整体再按竖向遮罩渐隐：顶栏高度（[barHeightPx]）的
 * [TopBarOpaqueBarFraction] 内不透明，其后到顶栏底边完全透明。整层严格限定在顶栏范围内。
 */
@Composable
private fun TopBarBlurLayer(backdrop: Backdrop, barHeightPx: Int) {
    val density = LocalDensity.current
    val tint = MaterialTheme.colorScheme.background
    val maskStops = remember(barHeightPx) { fadeStops(TopBarOpaqueBarFraction, 1f) }
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                drawRect(
                    brush = Brush.verticalGradient(colorStops = maskStops),
                    blendMode = BlendMode.DstIn,
                )
            },
    ) {
        // 由弱到强叠放：越靠上的区域，被越强的层覆盖
        for (index in 0 until TopBarBlurLayerCount) {
            val radius = TopBarBlurRadius * ((index + 1) / TopBarBlurLayerCount.toFloat())
            val layerMask = TopBarLayerMaskStops[index]
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        drawRect(
                            brush = Brush.verticalGradient(colorStops = layerMask),
                            blendMode = BlendMode.DstIn,
                        )
                    }
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { RectangleShape },
                        effects = { blur(with(density) { radius.toPx() }) },
                        highlight = null,
                        shadow = null,
                    ),
            )
        }
        // 底色叠层自身也按 smootherstep 在整个顶栏高度内逐渐变淡（而不是被整体遮罩在末段突然切掉），
        // 与各模糊层同步收敛到 0，才不会在顶栏底边留下一条底色硬边
        val tintStops = remember(tint) {
            Array(TopBarMaskSteps + 1) { i ->
                val t = i / TopBarMaskSteps.toFloat()
                val x = ((t - TopBarTintFadeStart) / (1f - TopBarTintFadeStart)).coerceIn(0f, 1f)
                val eased = x * x * x * (x * (x * 6f - 15f) + 10f)
                t to tint.copy(alpha = TopBarTintAlpha * (1f - eased))
            }
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(colorStops = tintStops)))
    }
}
