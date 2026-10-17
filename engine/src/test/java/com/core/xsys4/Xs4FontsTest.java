package com.core.xsys4;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import static org.junit.Assert.*;

public class Xs4FontsTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();

    @Test public void encodingWhitelistFallsBackToSjis() {
        assertEquals("gbk", Xs4Fonts.normalizeEncoding("gbk"));
        assertEquals("gbk", Xs4Fonts.normalizeEncoding(" GBK "));
        assertEquals("sjis", Xs4Fonts.normalizeEncoding("sjis"));
        assertEquals("sjis", Xs4Fonts.normalizeEncoding("utf8"));
        assertEquals("sjis", Xs4Fonts.normalizeEncoding(""));
        assertEquals("sjis", Xs4Fonts.normalizeEncoding(null));
    }

    @Test public void sjisDefaultsUseGothicAndMincho() {
        assertEquals("MTLc3m.ttf", Xs4Fonts.defaultGothic("sjis"));
        assertEquals("mincho.ttf", Xs4Fonts.defaultMincho("sjis"));
        Xs4Fonts.Resolved resolved = Xs4Fonts.resolve("sjis", null, null);
        assertEquals("MTLc3m.ttf", resolved.gothic);
        assertEquals("mincho.ttf", resolved.mincho);
        assertTrue(resolved.notes.isEmpty());
    }

    @Test public void gbkDefaultsUseDroidSansFallbackForBothSlots() {
        assertEquals("DroidSansFallback.ttf", Xs4Fonts.defaultGothic("gbk"));
        assertEquals("DroidSansFallback.ttf", Xs4Fonts.defaultMincho("gbk"));
        Xs4Fonts.Resolved resolved = Xs4Fonts.resolve("gbk", null, null);
        assertEquals("DroidSansFallback.ttf", resolved.gothic);
        assertEquals("DroidSansFallback.ttf", resolved.mincho);
        assertTrue(resolved.notes.isEmpty());
    }

    @Test public void blankCustomPathIsDefaultWithoutNote() {
        Xs4Fonts.Resolved resolved = Xs4Fonts.resolve("sjis", "  ", "");
        assertEquals("MTLc3m.ttf", resolved.gothic);
        assertEquals("mincho.ttf", resolved.mincho);
        assertTrue(resolved.notes.isEmpty());
    }

    @Test public void missingAbsoluteFontFallsBackToDefault() {
        String missing = new File(temp.getRoot(), "none.ttf").getAbsolutePath();
        assertNotNull(Xs4Fonts.rejectCustomFont(missing));
        Xs4Fonts.Resolved resolved = Xs4Fonts.resolve("sjis", missing, null);
        assertEquals("MTLc3m.ttf", resolved.gothic);
        assertEquals("mincho.ttf", resolved.mincho);
        assertEquals(1, resolved.notes.size());
    }

    @Test public void relativeFontPathFallsBackToDefault() {
        assertNotNull(Xs4Fonts.rejectCustomFont("fonts/a.ttf"));
        Xs4Fonts.Resolved resolved = Xs4Fonts.resolve("gbk", "fonts/a.ttf", null);
        assertEquals("DroidSansFallback.ttf", resolved.gothic);
        assertEquals(1, resolved.notes.size());
    }

    @Test public void wrongExtensionFallsBackToDefault() throws IOException {
        String path = write("a.txt").getAbsolutePath();
        assertNotNull(Xs4Fonts.rejectCustomFont(path));
        Xs4Fonts.Resolved resolved = Xs4Fonts.resolve("sjis", path, path);
        assertEquals("MTLc3m.ttf", resolved.gothic);
        assertEquals("mincho.ttf", resolved.mincho);
        assertEquals(2, resolved.notes.size());
    }

    @Test public void directoryNamedLikeFontFallsBackToDefault() throws IOException {
        String path = temp.newFolder("b.ttf").getAbsolutePath();
        assertNotNull(Xs4Fonts.rejectCustomFont(path));
        assertEquals("mincho.ttf", Xs4Fonts.resolve("sjis", null, path).mincho);
    }

    @Test public void validCustomFontsAreUsedPerSlot() throws IOException {
        String gothic = write("g.ttf").getAbsolutePath();
        String mincho = write("m.OTF").getAbsolutePath();
        assertNull(Xs4Fonts.rejectCustomFont(gothic));
        assertNull(Xs4Fonts.rejectCustomFont(mincho));
        Xs4Fonts.Resolved resolved = Xs4Fonts.resolve("sjis", gothic, mincho);
        assertEquals(gothic, resolved.gothic);
        assertEquals(mincho, resolved.mincho);
        assertTrue(resolved.notes.isEmpty());
    }

    @Test public void ttcAndOtcCollectionsAreAccepted() throws IOException {
        assertNull(Xs4Fonts.rejectCustomFont(write("c.ttc").getAbsolutePath()));
        assertNull(Xs4Fonts.rejectCustomFont(write("c.otc").getAbsolutePath()));
    }

    @Test public void customSlotsFallBackIndependently() throws IOException {
        String gothic = write("g.ttc").getAbsolutePath();
        Xs4Fonts.Resolved resolved = Xs4Fonts.resolve("gbk", gothic, "/nonexistent/m.ttf");
        assertEquals(gothic, resolved.gothic);
        assertEquals("DroidSansFallback.ttf", resolved.mincho);
        assertEquals(1, resolved.notes.size());
    }

    @Test public void nullAndBlankAreRejected() {
        assertNotNull(Xs4Fonts.rejectCustomFont(null));
        assertNotNull(Xs4Fonts.rejectCustomFont("   "));
    }

    private File write(String name) throws IOException {
        File file = new File(temp.getRoot(), name);
        Files.write(file.toPath(), new byte[] { 1 });
        return file;
    }
}
