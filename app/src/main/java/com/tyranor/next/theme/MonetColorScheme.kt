package com.tyranor.next.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import com.materialkolor.PaletteStyle as MaterialKolorPaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import com.materialkolor.dynamicColorScheme as materialKolorDynamicColorScheme

/**
 * 莫奈配色风格（Material You 调色板），与 InstallerX 的 PaletteStyle 对齐。
 * 每个风格决定种子色 → 全配色方案的色调映射算法。
 */
enum class PaletteStyle(val storageValue: String, val displayName: String) {
    TonalSpot("tonal_spot", "渐变点（默认）"),
    Neutral("neutral", "中性"),
    Vibrant("vibrant", "鲜艳"),
    Expressive("expressive", "表现"),
    Rainbow("rainbow", "彩虹"),
    FruitSalad("fruit_salad", "水果沙拉"),
    Monochrome("monochrome", "单色"),
    Fidelity("fidelity", "保真"),
    Content("content", "内容"),
    ;

    companion object {
        fun fromStorageValue(value: String): PaletteStyle =
            entries.find { it.storageValue == value } ?: TonalSpot
    }
}

/**
 * 用种子色生成完整的 Material 3 动态 ColorScheme（纯计算，可在后台线程调用）。
 * 规格固定用 Material 2021 版（Spec2021），全部风格均支持，避免 Expressive 风格
 * 组合在部分风格上缺失导致的降级分支。
 */
fun monetColorScheme(
    seedColor: Color,
    isDark: Boolean,
    style: PaletteStyle = PaletteStyle.TonalSpot,
): ColorScheme {
    val mkStyle = when (style) {
        PaletteStyle.TonalSpot -> MaterialKolorPaletteStyle.TonalSpot
        PaletteStyle.Neutral -> MaterialKolorPaletteStyle.Neutral
        PaletteStyle.Vibrant -> MaterialKolorPaletteStyle.Vibrant
        PaletteStyle.Expressive -> MaterialKolorPaletteStyle.Expressive
        PaletteStyle.Rainbow -> MaterialKolorPaletteStyle.Rainbow
        PaletteStyle.FruitSalad -> MaterialKolorPaletteStyle.FruitSalad
        PaletteStyle.Monochrome -> MaterialKolorPaletteStyle.Monochrome
        PaletteStyle.Fidelity -> MaterialKolorPaletteStyle.Fidelity
        PaletteStyle.Content -> MaterialKolorPaletteStyle.Content
    }
    return materialKolorDynamicColorScheme(
        seedColor = seedColor,
        isDark = isDark,
        style = mkStyle,
        contrastLevel = 0.0,
        specVersion = ColorSpec.SpecVersion.SPEC_2021,
    )
}

/** 调色盘预设种子色：Material 3 标准种子色板，点击直接作为手动主色。 */
data class PresetSeedColor(val key: String, val displayName: String, val color: Color)

val PresetSeedColors = listOf(
    PresetSeedColor("blue", "蓝色", Color(0xFF307DEF)),       // 与默认主题色一致
    PresetSeedColor("indigo", "靛蓝", Color(0xFF5355A9)),
    PresetSeedColor("purple", "紫罗兰", Color(0xFF6750A4)),
    PresetSeedColor("pink", "粉红", Color(0xFFB94073)),
    PresetSeedColor("red", "红色", Color(0xFFBA1A1A)),
    PresetSeedColor("orange", "橙色", Color(0xFF944A00)),
    PresetSeedColor("amber", "琥珀", Color(0xFF8C5300)),
    PresetSeedColor("yellow", "黄色", Color(0xFF795900)),
    PresetSeedColor("green", "绿色", Color(0xFF006D39)),
    PresetSeedColor("lime", "青柠", Color(0xFF5E6400)),
    PresetSeedColor("teal", "青色", Color(0xFF006874)),
    PresetSeedColor("cyan", "天蓝", Color(0xFF006A64)),
    PresetSeedColor("blue_grey", "蓝灰", Color(0xFF575D7E)),
    PresetSeedColor("brown", "棕色", Color(0xFF7D524A)),
    PresetSeedColor("grey", "灰色", Color(0xFF5F6162)),
)
