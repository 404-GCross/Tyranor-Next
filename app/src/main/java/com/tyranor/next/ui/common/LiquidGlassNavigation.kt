package com.tyranor.next.ui.common

import android.os.Build
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import com.tyranor.next.settings.AppSettingsStore
import com.tyranor.next.theme.AppThemeColors
import kotlin.math.abs
import kotlin.math.roundToInt

/** 底部导航栏的液态玻璃导航项（文字 + 图标资源）。 */
data class LiquidGlassNavItem(
    val label: String,
    @DrawableRes val iconRes: Int,
)

/**
 * 圆角液态玻璃底部导航栏（参考 RinneMobile 流体玻璃导航样式）：
 * 通过 [com.kyant.backdrop] 对页面内容做 vibrancy + blur + lens 采样，呈现“看穿”的毛玻璃质感；
 * 选中项有跟随的主题色玻璃焦点胶囊；支持长按后左右拖动切换页面（移植自 RinneMobile）。
 * 悬浮于内容之上，圆角 16dp。
 */
@Composable
fun LiquidGlassNavigationBar(
    backdrop: Backdrop,
    selectedIndex: Int,
    primaryColor: Color,
    unselectedColor: Color,
    items: List<LiquidGlassNavItem>,
    onItemClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    // 玻璃表面色随外观模式：深色模式用深色表面
    val surfaceColor = if (AppThemeColors.isDark) Color(0xFF17191C) else Color.White
    val mutedColor = unselectedColor
    // 统一圆角（AGENT.md）：圆角组件一律 8dp；液态玻璃导航在 8dp 基础上加大 8dp，视觉更圆润
    val shape = RoundedCornerShape(16.dp)
    // blur/lens 依赖 RenderEffect（Android 12+ 才生效）。高版本表面半透明以透出模糊内容
    // 呈现液态玻璃质感；低版本（<12）无实时模糊，直接用不透明实底，避免文字等内容透出。
    val glassSurfaceAlpha = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) 0.55f else 1f
    val focusSurfaceAlpha = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) 0.38f else 0.75f
    val currentSelectedIndex by rememberUpdatedState(selectedIndex)
    val currentOnItemClick by rememberUpdatedState(onItemClick)
    var navWidth by remember { mutableIntStateOf(0) }
    // 长按滑动切换：拖动期间焦点胶囊跟随手指，松手回弹到选中项（移植自 RinneMobile）
    var dragPosition by remember { mutableStateOf<Float?>(null) }
    var lastDragIndex by remember { mutableIntStateOf(-1) }
    // 每个 item 的实际中心 x（相对导航栏内容区），指示器按真实位置定位，避免估算误差导致最右歪斜
    var itemCenters by remember { mutableStateOf<Map<Int, Float>>(emptyMap()) }
    val focusWidth = 76.dp
    val focusWidthPx = with(density) { focusWidth.toPx() }
    // onSizeChanged 在 Box 的水平 padding 之后测量，navWidth 即 item 分布的内容宽度（仅拖动估算用）
    val itemWidth = if (items.isEmpty()) 0f else navWidth / items.size.toFloat()

    fun centerAt(position: Float): Float {
        val floorIdx = position.toInt().coerceIn(items.indices)
        val frac = (position - floorIdx).coerceIn(0f, 1f)
        val start = itemCenters[floorIdx] ?: itemWidth * (floorIdx + 0.5f)
        val end = itemCenters[floorIdx + 1] ?: itemWidth * (floorIdx + 1 + 0.5f)
        return start + (end - start) * frac
    }

    val focusTargetX = dragPosition?.let { position ->
        centerAt(position) - focusWidthPx / 2f
    } ?: ((itemCenters[currentSelectedIndex] ?: itemWidth * (currentSelectedIndex + 0.5f)) - focusWidthPx / 2f)
    // stiffness 提高、阻尼加大：指示器跟随更干脆，点击切换不拖沓
    val focusX by animateFloatAsState(
        targetValue = focusTargetX,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = 1200f),
        label = "liquidGlassFocus",
    )
    // 流体拉伸（移植自 RinneMobile）：拖动跟随时胶囊横向拉长、纵向压缩，静止后回弹
    val movement = if (itemWidth > 0f) abs(focusTargetX - focusX) / itemWidth else 0f
    val stretch = movement.coerceIn(0f, 1f) * 0.42f

    fun updateSelectedFragment(position: Float) {
        val index = position
            .roundToInt()
            .coerceIn(items.indices)
        if (index != lastDragIndex) {
            lastDragIndex = index
            if (index != currentSelectedIndex) currentOnItemClick(index)
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 12.dp)
            .height(64.dp)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { shape },
                effects = {
                    vibrancy()
                    blur(with(density) { 24.dp.toPx() })
                    lens(
                        with(density) { 28.dp.toPx() },
                        with(density) { 34.dp.toPx() },
                    )
                },
                highlight = { Highlight.Default.copy(alpha = 0.85f) },
                shadow = { Shadow.Default.copy(alpha = 0.8f) },
                // 表面半透明让模糊内容透出（玻璃质感），大半径模糊保证内容不清晰
                onDrawSurface = { drawRect(surfaceColor.copy(alpha = glassSurfaceAlpha)) },
            )
            .onSizeChanged { navWidth = it.width }
            .pointerInput(Unit) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { position ->
                        if (itemWidth <= 0f) return@detectDragGesturesAfterLongPress
                        val index = (position.x / itemWidth)
                            .toInt()
                            .coerceIn(items.indices)
                        lastDragIndex = currentSelectedIndex
                        dragPosition = index.toFloat()
                        updateSelectedFragment(index.toFloat())
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        if (itemWidth > 0f) {
                            val position = ((dragPosition ?: currentSelectedIndex.toFloat()) +
                                dragAmount.x / itemWidth)
                                .coerceIn(0f, items.lastIndex.toFloat())
                            dragPosition = position
                            updateSelectedFragment(position)
                        }
                    },
                    onDragEnd = {
                        dragPosition = null
                        lastDragIndex = -1
                    },
                    onDragCancel = {
                        dragPosition = null
                        lastDragIndex = -1
                    },
                )
            },
    ) {
        // 选中焦点胶囊：主题色半透明的液态玻璃效果
        // 用 offset 而非 graphicsLayer 平移：offset 会更新布局坐标，
        // drawBackdrop 采样与实际位置一致，避免胶囊中间出现错位线条
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                // offset 移动位置（布局坐标跟随，drawBackdrop 采样不偏移）；
                // graphicsLayer 只做流体拉伸缩放（纯视觉，不影响采样坐标）
                .offset { IntOffset(focusX.roundToInt(), 0) }
                .graphicsLayer {
                    scaleX = 1f + stretch
                    scaleY = 1f - stretch * 0.25f
                }
                .width(focusWidth)
                .height(48.dp)
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { shape },
                    effects = {
                        vibrancy()
                        blur(with(density) { 18.dp.toPx() })
                        // 注意：不加 lens——折射会在胶囊中间形成分界线
                    },
                    // 强化顶部高光与阴影：胶囊呈现玻璃厚度感，液态效果更明显
                    highlight = { Highlight.Default.copy(alpha = 1f) },
                    shadow = { Shadow.Default.copy(alpha = 0.85f) },
                    // 主题色浓度适中：透出胶囊内的模糊内容，而不是盖成实色块
                    onDrawSurface = { drawRect(primaryColor.copy(alpha = focusSurfaceAlpha)) },
                ),
        )

        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEachIndexed { index, item ->
                LiquidGlassNavItemView(
                    item = item,
                    selected = index == selectedIndex,
                    primaryColor = primaryColor,
                    mutedColor = mutedColor,
                    onClick = { onItemClick(index) },
                    onCenterChanged = { centerX ->
                        itemCenters = itemCenters + (index to centerX)
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun LiquidGlassNavItemView(
    item: LiquidGlassNavItem,
    selected: Boolean,
    primaryColor: Color,
    mutedColor: Color,
    onClick: () -> Unit,
    onCenterChanged: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val contentColor = if (selected) primaryColor else mutedColor
    Box(
        modifier = modifier
            .height(56.dp)
            .clip(RoundedCornerShape(16.dp))
            .onGloballyPositioned { coords ->
                // 相对父级 Row（即导航栏内容区）的中心 x
                onCenterChanged(coords.positionInParent().x + coords.size.width / 2f)
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(item.iconRes),
            contentDescription = item.label,
            modifier = Modifier.size(26.dp),
            contentScale = ContentScale.Fit,
            colorFilter = ColorFilter.tint(contentColor),
        )
    }
}

/**
 * 液态玻璃导航悬浮时的列表底部滚动留白：
 * 内容可滚动经过玻璃后面（沉浸），但列表尾部预留导航高度（64+12*2+系统导航条），
 * 保证滚动到底时最后一项完全露出不被遮挡。非液态玻璃模式返回 0。
 */
@Composable
fun glassNavBottomInset(): Dp {
    return if (AppSettingsStore.navStyleState.value == AppSettingsStore.NAV_STYLE_LIQUID_GLASS) {
        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 88.dp
    } else {
        0.dp
    }
}
