package com.core.siglus;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;

import static org.junit.Assert.*;

public final class SiglusStageSizeTest {
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();
    private static final String KEY = "key = [0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15]\n";

    @Test public void readsGeneratedArrayAndHeaderWrappedUtf16Configuration() throws Exception {
        File root = game();
        write(root, "Gameexe.dat", encrypted(config(1920, 1080), 1));
        assertSize(root, 1920, 1080);
    }

    @Test public void acceptsMultilineArrayCommentsAndLegacyHexKey() throws Exception {
        File root = game();
        write(root, "key.toml", "# engine generated\nkey = [0, 1, 2, 3, # comment\n4,5,6,7,8,9,10,11,12,13,14,15,]\n");
        write(root, "Gameexe.dat", encrypted(config(1600, 900), 1));
        assertSize(root, 1600, 900);
        write(root, "key.toml", "key_hex=\"00010203_04050607 08090A0B0C0D0E0F\"\n");
        assertSize(root, 1600, 900);
    }

    @Test public void generatedArrayTakesPrecedenceOverHex() throws Exception {
        File root = game();
        write(root, "key.toml", KEY + "key_hex=\"FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF\"\n");
        write(root, "Gameexe.dat", encrypted(config(1280, 720), 1));
        assertSize(root, 1280, 720);
    }

    @Test public void wrongOrMissingKeyNeverUsesAnotherGameOrChsFile() throws Exception {
        File root = game();
        write(root, "Gameexe.dat", encrypted(config(1920, 1080), 1));
        write(root, "Gameexe.chs", config(800, 600));
        write(root, "key.toml", "key_hex=\"FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF\"\n");
        assertNull(SiglusStageSize.read(root));
        assertTrue(new File(root, "key.toml").delete());
        assertNull(SiglusStageSize.read(root));
        write(root, "key.toml", KEY);
        assertSize(root, 1920, 1080);
    }

    @Test public void firstValidSelectAppendControlsConfigurationSelection() throws Exception {
        File root = game();
        File first = new File(root, "First"), second = new File(root, "Second");
        assertTrue(first.mkdir()); assertTrue(second.mkdir());
        write(root, "Select.ini", "invalid\tname\textra\nFirst\tEpisode 1\nSecond\tEpisode 2\n");
        write(first, "Gameexe.ini", config(1600, 900));
        write(second, "Gameexe.ini", config(800, 600));
        write(root, "Gameexe.dat", encrypted(config(1920, 1080), 1));
        assertSize(root, 1600, 900);
        assertTrue(new File(first, "Gameexe.ini").delete());
        assertNull(SiglusStageSize.read(root));
    }

    @Test public void followsDatThenIniThenLocalizationOrderWithoutFallbackAfterSelection() throws Exception {
        File root = game();
        write(root, "GameexeZH.ini", config(1600, 900));
        write(root, "GameexeEN.ini", config(1280, 720));
        assertSize(root, 1280, 720);
        write(root, "Gameexe.ini", config(800, 600));
        assertSize(root, 800, 600);
        write(root, "Gameexe.dat", "invalid");
        assertNull(SiglusStageSize.read(root));
    }

    @Test public void rejectsAppendTraversalAndAbsolutePaths() throws Exception {
        File root = game();
        File outside = temporary.newFolder();
        write(outside, "Gameexe.ini", config(1920, 1080));
        write(root, "Select.ini", "../" + outside.getName() + "\tOutside\n");
        assertNull(SiglusStageSize.read(root));
        // Canonical containment is exercised without requiring symlink privileges on Windows.
        write(root, "Select.ini", outside.getAbsolutePath() + "\tOutside\n");
        assertNull(SiglusStageSize.read(root));
    }

    @Test public void laterDefinitionWinsIncludingInvalidDimensions() throws Exception {
        File root = game();
        write(root, "Gameexe.ini", config(1280, 720) + "screen_size = \"1920\", \"1080\" ; annotation\n");
        assertSize(root, 1920, 1080);
        for (String value : new String[]{"0,720", "999999999999,720", "-1,720", "8192,8192", "640", "abc,720"}) {
            write(root, "Gameexe.ini", config(1280, 720) + "#SCREEN_SIZE=" + value + "\n");
            assertNull(value, SiglusStageSize.read(root));
        }
    }

    @Test public void absentSizeAndMissingConfigurationNeverGuessDefaults() throws Exception {
        File root = game();
        assertNull(SiglusStageSize.read(root));
        write(root, "Gameexe.ini", "#TITLE=\"sample\"\n");
        assertNull(SiglusStageSize.read(root));
    }

    @Test public void rejectsDecoderExtensionsInsteadOfIgnoringThem() throws Exception {
        File root = game();
        write(root, "Gameexe.dat", encrypted(config(1920, 1080), 1));
        for (String option : new String[]{"base_angou_code=[]", "base_angou_hex=\"00\"", "game_angou_code=[0]",
                "game_angou_hex=\"00\"", "chain_order=[\"game\"]", "unknown_decode_mode=1"}) {
            write(root, "key.toml", KEY + option + "\n");
            assertNull(option, SiglusStageSize.read(root));
        }
        write(root, "key.toml", KEY + "emote_key=0x1234\noverride_string_encryption=\"none\"\n");
        assertSize(root, 1920, 1080);
    }

    @Test public void rejectsMalformedAndDuplicateKeyAssignments() throws Exception {
        File root = game();
        write(root, "Gameexe.ini", config(1920, 1080));
        for (String key : new String[]{"key=[0,1]", "key=[256,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15]",
                KEY + KEY, "key=[0,1\nkey_hex=\"000102030405060708090A0B0C0D0E0F\"", "key_hex=\"00\""}) {
            write(root, "key.toml", key);
            assertNull(SiglusStageSize.read(root));
        }
    }

    @Test public void acceptsMaskOnlyAndPlainDatAndRawCompressedFormats() throws Exception {
        File root = game();
        write(root, "Gameexe.dat", encrypted(config(1920, 1080), 0));
        assertSize(root, 1920, 1080);
        write(root, "Gameexe.dat", config(1920, 1080));
        assertSize(root, 1920, 1080);
        write(root, "Gameexe.dat", literalArchive(config(1920, 1080).getBytes(StandardCharsets.UTF_16LE)));
        assertSize(root, 1920, 1080);
    }

    @Test public void truncationAndUntrustedOutputSizeCannotProduceAStage() throws Exception {
        File root = game();
        byte[] valid = encrypted(config(1920, 1080), 1);
        for (int size : new int[]{0, 4, 8, valid.length - 10}) {
            write(root, "Gameexe.dat", Arrays.copyOf(valid, size));
            assertNull(SiglusStageSize.read(root));
        }
        byte[] huge = {9,0,0,0,-1,-1,-1,127,0};
        write(root, "Gameexe.dat", huge);
        assertNull(SiglusStageSize.read(root));
        byte[] invalidBackref = {11,0,0,0,40,0,0,0,0,0,0};
        write(root, "Gameexe.dat", invalidBackref);
        assertNull(SiglusStageSize.read(root));
    }

    @Test public void interruptedWorkerReturnsNoResultAndKeepsInterruptFlag() throws Exception {
        File root = game();
        write(root, "Gameexe.ini", config(1920, 1080));
        Thread.currentThread().interrupt();
        try {
            assertNull(SiglusStageSize.read(root));
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }

    @Test public void decodesIndependentArchiveWithDistanceBackreference() throws Exception {
        File root = game();
        byte[] archive = {42, 0, 0, 0, 33, 0, 0, 0, -33, 35, 65, 61, 49, 10, 83, 0, 35, 83, -1, 67, 82, 69, 69, 78, 95, 83, 73, -1, 90, 69, 61, 49, 57, 50, 48, 44, 31, 49, 48, 56, 48, 10};
        write(root, "Gameexe.dat", archive);
        assertSize(root, 1920, 1080);
    }

    @Test public void followsCaseInsensitiveNamesAndNeverRewritesInputFiles() throws Exception {
        File root = game();
        assertTrue(new File(root, "key.toml").delete());
        String key = KEY + "# preserve this user comment\n";
        write(root, "KEY.TOML", key);
        byte[] config = encrypted(config(1920, 1080), 1);
        write(root, "GAMEEXE.DAT", config);
        assertSize(root, 1920, 1080);
        assertArrayEquals(key.getBytes(StandardCharsets.UTF_8), Files.readAllBytes(new File(root, "KEY.TOML").toPath()));
        assertArrayEquals(config, Files.readAllBytes(new File(root, "GAMEEXE.DAT").toPath()));
        assertEquals(2, root.list().length);
    }

    @Test public void configurationBecomesAvailableAfterEngineCreatesItsOwnKey() throws Exception {
        File root = temporary.newFolder();
        write(root, "Gameexe.dat", encrypted(config(1920, 1080), 1));
        assertNull(SiglusStageSize.read(root));
        write(root, "key.toml", KEY);
        assertSize(root, 1920, 1080);
    }

    @Test public void rejectsCanonicalSymlinkEscapeWhenPlatformSupportsLinks() throws Exception {
        File root = game();
        File outside = temporary.newFolder();
        write(outside, "Gameexe.ini", config(1920, 1080));
        try {
            Files.createSymbolicLink(new File(root, "linked").toPath(), outside.toPath());
        } catch (UnsupportedOperationException | java.nio.file.FileSystemException unavailable) {
            org.junit.Assume.assumeNoException(unavailable);
        }
        write(root, "Select.ini", "linked\tOutside\n");
        assertNull(SiglusStageSize.read(root));
    }

    @Test public void plaintextIniDoesNotRequireAGeneratedKey() throws Exception {
        File root = temporary.newFolder();
        write(root, "Gameexe.ini", config(1920, 1080));
        assertSize(root, 1920, 1080);
        assertFalse(new File(root, "key.toml").exists());
    }

    @Test public void headerModeZeroUsesTheFixedMaskWithoutAnExeKey() throws Exception {
        File root = temporary.newFolder();
        write(root, "Gameexe.dat", encrypted(config(1280, 720), 0));
        assertSize(root, 1280, 720);
    }

    @Test public void headerModeOneNeverGuessesAMissingExeKey() throws Exception {
        File root = temporary.newFolder();
        write(root, "Gameexe.dat", encrypted(config(1920, 1080), 1));
        assertNull(SiglusStageSize.read(root));
        write(root, "key.toml", KEY);
        assertSize(root, 1920, 1080);
    }

    @Test public void headerlessMaskOnlyFollowsTheOptionalExeStepContract() throws Exception {
        File root = temporary.newFolder();
        byte[] payload = literalArchive(config(1600, 900).getBytes(StandardCharsets.UTF_16LE));
        SiglusGameexeMask.apply(payload);
        write(root, "Gameexe.dat", payload);
        assertSize(root, 1600, 900);
        // Unrelated Scene-only settings do not introduce a nonexistent EXE key.
        write(root, "key.toml", "override_string_encryption=\"none\"\n");
        assertSize(root, 1600, 900);
        write(root, "key.toml", "game_angou_hex=\"00\"\n");
        assertNull(SiglusStageSize.read(root));
    }

    @Test public void presentUnsupportedDecoderOptionsAlsoRejectPlaintextIni() throws Exception {
        File root = temporary.newFolder();
        write(root, "Gameexe.ini", config(1920, 1080));
        write(root, "key.toml", "base_angou_hex=\"00\"\n");
        assertNull(SiglusStageSize.read(root));
    }

    private File game() throws Exception {
        File root = temporary.newFolder();
        write(root, "key.toml", KEY);
        return root;
    }

    private static String config(int width, int height) {
        return "#TITLE=\"generated fixture\"\n#START_SCENE=\"start\"\n#SCREEN_SIZE=" + width + "," + height + "\n";
    }

    private static void assertSize(File root, int width, int height) {
        SiglusStageSize.Size size = SiglusStageSize.read(root);
        assertNotNull(size);
        assertEquals(width, size.width); assertEquals(height, size.height);
    }

    private static void write(File root, String name, String text) throws Exception {
        write(root, name, text.getBytes(StandardCharsets.UTF_8));
    }

    private static void write(File root, String name, byte[] bytes) throws Exception {
        Files.write(new File(root, name).toPath(), bytes);
    }

    private static byte[] encrypted(String text, int mode) throws Exception {
        byte[] payload = literalArchive(text.getBytes(StandardCharsets.UTF_16LE));
        SiglusGameexeMask.apply(payload);
        if (mode != 0) for (int i = 0; i < payload.length; i++) payload[i] ^= i & 15;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        littleEndian(output, 1); littleEndian(output, mode); output.write(payload);
        return output.toByteArray();
    }

    private static byte[] literalArchive(byte[] text) throws Exception {
        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        for (int start = 0; start < text.length; start += 8) {
            payload.write(255); payload.write(text, start, Math.min(8, text.length - start));
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        littleEndian(output, 8 + payload.size()); littleEndian(output, text.length); payload.writeTo(output);
        return output.toByteArray();
    }

    private static void littleEndian(ByteArrayOutputStream output, int value) {
        for (int i = 0; i < 4; i++) output.write((value >>> (8 * i)) & 255);
    }
}
