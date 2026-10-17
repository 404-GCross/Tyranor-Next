package com.core.xsys4;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * System4（xsystem4）字体与文本编码的解析，纯 Java 且不依赖 Android，便于 JVM 单测。
 *
 * <p>内置映射的字体 asset 名与 System35 / ONS 共用同一批字体（见 {@code engine/xsystem35/BUILD.md}）；
 * 改动这里的文件名须同步检查 System35 与 ONS 的字体依赖。引擎加载 {@code --font-*} 失败是致命错误，
 * 因此自定义字体必须先经 {@link #rejectCustomFont} 校验，任何不可用的路径都回落到内置映射。
 */
public final class Xs4Fonts {

    public static final String ENCODING_SJIS = "sjis";
    public static final String ENCODING_GBK = "gbk";

    static final String GOTHIC_SJIS = "MTLc3m.ttf";
    static final String MINCHO_SJIS = "mincho.ttf";
    static final String GBK_FALLBACK = "DroidSansFallback.ttf";

    private static final String[] FONT_EXTENSIONS = { ".ttf", ".ttc", ".otf", ".otc" };

    private Xs4Fonts() {
    }

    /** 文本编码白名单（大小写与首尾空白不敏感）；空值与非法值均回落 {@link #ENCODING_SJIS}。 */
    public static String normalizeEncoding(String raw) {
        if (raw != null && ENCODING_GBK.equals(raw.trim().toLowerCase(Locale.ROOT))) {
            return ENCODING_GBK;
        }
        return ENCODING_SJIS;
    }

    /** 编码对应的内置 gothic 字体 asset 名。 */
    public static String defaultGothic(String encoding) {
        return ENCODING_GBK.equals(normalizeEncoding(encoding)) ? GBK_FALLBACK : GOTHIC_SJIS;
    }

    /** 编码对应的内置 mincho 字体 asset 名。 */
    public static String defaultMincho(String encoding) {
        return ENCODING_GBK.equals(normalizeEncoding(encoding)) ? GBK_FALLBACK : MINCHO_SJIS;
    }

    /**
     * 校验自定义字体路径：必须绝对、扩展名在白名单（ttf/ttc/otf/otc）、是存在的普通文件且可读。
     *
     * @return {@code null} 表示可用；否则返回拒绝原因（供宿主记日志）
     */
    public static String rejectCustomFont(String rawPath) {
        if (rawPath == null || rawPath.trim().isEmpty()) {
            return "empty path";
        }
        File file = new File(rawPath.trim());
        if (!file.isAbsolute()) {
            return "not absolute";
        }
        if (!hasFontExtension(file.getName())) {
            return "unsupported extension";
        }
        if (!file.isFile()) {
            return "missing";
        }
        if (!file.canRead()) {
            return "unreadable";
        }
        return null;
    }

    /**
     * 解析最终传给引擎的 gothic / mincho 字体。自定义路径未设置时静默使用内置映射；
     * 设置了但校验失败时使用内置映射并在 {@link Resolved#notes} 留下原因。
     */
    public static Resolved resolve(String encoding, String customGothic, String customMincho) {
        String normalized = normalizeEncoding(encoding);
        List<String> notes = new ArrayList<>();
        String gothic = pick(customGothic, "gothic", defaultGothic(normalized), notes);
        String mincho = pick(customMincho, "mincho", defaultMincho(normalized), notes);
        return new Resolved(gothic, mincho, notes);
    }

    private static String pick(String custom, String slot, String fallback, List<String> notes) {
        if (custom == null || custom.trim().isEmpty()) {
            return fallback;
        }
        String reason = rejectCustomFont(custom);
        if (reason == null) {
            return custom.trim();
        }
        notes.add(slot + " font " + custom.trim() + " rejected (" + reason + "), using " + fallback);
        return fallback;
    }

    private static boolean hasFontExtension(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        for (String extension : FONT_EXTENSIONS) {
            if (lower.endsWith(extension)) {
                return true;
            }
        }
        return false;
    }

    /** {@link #resolve} 的结果：gothic / mincho 为传给引擎的值（绝对路径或内置 asset 名）。 */
    public static final class Resolved {
        public final String gothic;
        public final String mincho;
        /** 自定义字体回落原因（英文，供日志）；全部采用时为空。 */
        public final List<String> notes;

        Resolved(String gothic, String mincho, List<String> notes) {
            this.gothic = gothic;
            this.mincho = mincho;
            this.notes = Collections.unmodifiableList(notes);
        }
    }
}
