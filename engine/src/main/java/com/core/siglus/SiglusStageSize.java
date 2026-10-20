package com.core.siglus;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Read-only host implementation of the Gameexe format and initial-selection contract.
 * Format reference: xmoezzz/siglus_rs at 5f358ef1360b0439e92aed2b749e8f25d8721954,
 * resource.rs, host.rs, gameexe.rs, key_toml.rs and lzss.rs. No native code is copied.
 * Only the separate SiglusGameexeMask data file is imported under MPL-2.0.
 */
final class SiglusStageSize {
    private static final int MAX_CONFIG_BYTES = 4 * 1024 * 1024;
    private static final int MAX_TEXT_BYTES = 16 * 1024 * 1024;
    private static final int MAX_KEY_BYTES = 64 * 1024;
    private static final int MAX_DIRECTORY_ENTRIES = 8192;
    private static final int MAX_STAGE_SIDE = 8192;
    private static final long MAX_STAGE_PIXELS = 32L * 1024 * 1024;
    private static final Charset SHIFT_JIS = Charset.forName("windows-31j");
    private static final String[] GAMEEXE_NAMES = {
            "Gameexe.dat", "Gameexe.ini", "GameexeEN.dat", "GameexeEN.ini",
            "GameexeZH.dat", "GameexeZH.ini", "GameexeZHTW.dat", "GameexeZHTW.ini",
            "GameexeDE.dat", "GameexeDE.ini", "GameexeES.dat", "GameexeES.ini",
            "GameexeFR.dat", "GameexeFR.ini", "GameexeID.dat", "GameexeID.ini"
    };

    private SiglusStageSize() {}

    static final class Size {
        final int width;
        final int height;

        Size(int width, int height) {
            this.width = width;
            this.height = height;
        }
    }

    /** Returns null unless this game's selected configuration can be decoded safely. */
    static Size read(File gameRoot) {
        if (gameRoot == null) return null;
        try {
            checkInterrupted();
            File root = gameRoot.getCanonicalFile();
            if (!root.isDirectory()) return null;
            File base = initialAppend(root);
            if (base == null) return null;
            for (String name : GAMEEXE_NAMES) {
                File config = child(root, base, name);
                if (config == null || !config.isFile()) continue;
                File keyFile = child(root, root, "key.toml");
                // Plaintext and mask-only titles need not have generated an EXE key.
                // A present file is still parsed, so unsupported decoder options cannot
                // silently diverge from the engine's configuration.
                byte[] key = keyFile == null ? null
                        : parseKey(decode(readBytes(keyFile, MAX_KEY_BYTES), StandardCharsets.UTF_8));
                byte[] bytes = readBytes(config, MAX_CONFIG_BYTES);
                String text = name.endsWith(".ini")
                        ? decode(bytes, StandardCharsets.UTF_8) : decodeDat(bytes, key);
                // Never try a lower-priority file after the engine selected this one.
                return text == null ? null : screenSize(text);
            }
        } catch (IOException | IllegalArgumentException | SecurityException ignored) {
            // Unsupported input disables this optional feature; never alter the game/key.
        }
        return null;
    }

    private static File initialAppend(File root) throws IOException {
        File select = child(root, root, "Select.ini");
        if (select == null || !select.isFile()) return root;
        byte[] bytes = readBytes(select, MAX_KEY_BYTES);
        String text;
        try {
            text = decode(bytes, StandardCharsets.UTF_8);
        } catch (CharacterCodingException invalidUtf8) {
            text = decode(bytes, SHIFT_JIS);
        }
        for (String line : text.split("\n", -1)) {
            if (line.endsWith("\r")) line = line.substring(0, line.length() - 1);
            if (line.isEmpty()) continue;
            String[] columns = line.split("\t", -1);
            if (columns.length > 2) continue;
            String append = columns[0];
            if (append.isEmpty()) return root;
            // A literal backslash is not a directory separator on the engine's Android
            // Path.join route. Unsupported Windows-style/absolute paths fail closed.
            if (append.indexOf('\\') >= 0 || append.indexOf(':') >= 0
                    || new File(append).isAbsolute()) return null;
            File current = root;
            for (String part : append.split("/", -1)) {
                if (part.equals("..")) return null;
                if (part.isEmpty() || part.equals(".")) continue;
                current = child(root, current, part);
                if (current == null || !current.isDirectory()) return null;
            }
            return current;
        }
        return root;
    }

    /** Exact names win, otherwise accept exactly one ASCII-case-insensitive match. */
    private static File child(File root, File parent, String name) throws IOException {
        checkInterrupted();
        File exact = new File(parent, name);
        if (exact.exists()) return contained(root, exact);
        String[] names = parent.list();
        if (names == null || names.length > MAX_DIRECTORY_ENTRIES) throw new IOException("directory unavailable");
        File found = null;
        for (String candidate : names) {
            if (!asciiEquals(name, candidate)) continue;
            if (found != null) throw new IOException("ambiguous filename");
            found = contained(root, new File(parent, candidate));
        }
        return found;
    }

    private static boolean asciiEquals(String left, String right) {
        if (left.length() != right.length()) return false;
        for (int i = 0; i < left.length(); i++) {
            char a = left.charAt(i), b = right.charAt(i);
            if (a >= 'A' && a <= 'Z') a += 'a' - 'A';
            if (b >= 'A' && b <= 'Z') b += 'a' - 'A';
            if (a != b) return false;
        }
        return true;
    }

    private static File contained(File root, File path) throws IOException {
        File canonical = path.getCanonicalFile();
        String base = root.getPath();
        if (!canonical.equals(root) && !canonical.getPath().startsWith(base + File.separator)) {
            throw new IOException("path outside game");
        }
        return canonical;
    }

    private static byte[] readBytes(File path, int limit) throws IOException {
        long length = path.length();
        if (length > limit) throw new IOException("input too large");
        try (FileInputStream input = new FileInputStream(path);
                ByteArrayOutputStream output = new ByteArrayOutputStream((int) Math.min(length, 8192))) {
            byte[] buffer = new byte[8192];
            for (int count; (count = input.read(buffer)) != -1;) {
                checkInterrupted();
                if (count > limit - output.size()) throw new IOException("input grew past limit");
                output.write(buffer, 0, count);
            }
            return output.toByteArray();
        }
    }

    private static byte[] parseKey(String text) throws IOException {
        Map<String, String> values = new HashMap<>();
        String pending = null;
        StringBuilder array = new StringBuilder();
        for (String raw : text.split("\n", -1)) {
            String line = comment(raw, '#').trim();
            if (line.isEmpty()) continue;
            if (pending != null) {
                if (line.indexOf('=') >= 0 || line.startsWith("[")) throw new IOException("broken key array");
                array.append(line);
                if (line.contains("]")) {
                    values.put(pending, array.toString());
                    pending = null;
                }
                continue;
            }
            // The engine's helper scans assignment names irrespective of TOML tables.
            if (line.startsWith("[")) continue;
            int equal = line.indexOf('=');
            if (equal < 0) throw new IOException("unsupported key syntax");
            String name = line.substring(0, equal).trim();
            String value = line.substring(equal + 1).trim();
            // These options change Gameexe decoding. Do not silently use the default mask.
            if (!name.equals("key") && !name.equals("key_hex")
                    && !name.equals("emote_key") && !name.equals("override_string_encryption")) {
                throw new IOException("unsupported decoder option");
            }
            if (values.containsKey(name)) throw new IOException("duplicate key assignment");
            if (value.startsWith("[") && !value.contains("]")) {
                pending = name;
                array.setLength(0);
                array.append(value);
            } else values.put(name, value);
        }
        if (pending != null) throw new IOException("unfinished key array");
        String value = values.containsKey("key") ? values.get("key") : values.get("key_hex");
        if (value == null) return null;
        byte[] result = new byte[16];
        if (value.startsWith("[") && value.endsWith("]")) {
            String[] parts = value.substring(1, value.length() - 1).split(",", -1);
            int count = 0;
            for (int i = 0; i < parts.length; i++) {
                String part = parts[i].trim().replace("_", "");
                if (part.isEmpty() && i == parts.length - 1) continue;
                if (count >= result.length || part.isEmpty()) throw new IOException("invalid key length");
                int number = part.startsWith("0x") || part.startsWith("0X")
                        ? Integer.parseInt(part.substring(2), 16) : Integer.parseInt(part, 10);
                if (number < 0 || number > 255) throw new IOException("invalid key byte");
                result[count++] = (byte) number;
            }
            if (count != 16) throw new IOException("invalid key length");
        } else {
            if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
                value = value.substring(1, value.length() - 1);
            }
            // Match the upstream hex helper's ASCII-hex filtering, including separators.
            value = value.replaceAll("[^0-9a-fA-F]", "");
            if (value.length() != 32) throw new IOException("invalid hex key length");
            for (int i = 0; i < 16; i++) result[i] = (byte) Integer.parseInt(value.substring(2 * i, 2 * i + 2), 16);
        }
        return result;
    }

    private static String decodeDat(byte[] raw, byte[] key) throws IOException {
        if (raw.length >= 8 && (uint(raw, 4) == 0 || key != null)) {
            byte[] payload = Arrays.copyOfRange(raw, 8, raw.length);
            if (uint(raw, 4) != 0) xor(payload, key);
            SiglusGameexeMask.apply(payload);
            String text = decodedPayload(payload, true);
            if (text != null) return text;
        }
        String text = decodedPayload(raw, false);
        if (text != null) return text;
        byte[] masked = raw.clone();
        // Upstream's default headerless chain skips the optional EXE step when
        // no per-game key is configured; its fixed Gameexe step still applies.
        if (key != null) xor(masked, key);
        SiglusGameexeMask.apply(masked);
        return decodedPayload(masked, false);
    }

    private static void xor(byte[] data, byte[] key) {
        for (int i = 0; i < data.length; i++) data[i] ^= key[i & 15];
    }

    private static String decodedPayload(byte[] bytes, boolean lzssFirst) throws IOException {
        checkInterrupted();
        String text;
        if (lzssFirst) {
            byte[] inflated = unpack(bytes);
            if (inflated != null && (text = guessText(inflated)) != null) return text;
        }
        if ((text = guessText(bytes)) != null) return text;
        if (!lzssFirst) {
            byte[] inflated = unpack(bytes);
            if (inflated != null) return guessText(inflated);
        }
        return null;
    }

    private static byte[] unpack(byte[] bytes) throws IOException {
        if (bytes.length < 8) return null;
        long boundary = uint(bytes, 0), size = uint(bytes, 4);
        int end = (int) Math.min(boundary, bytes.length);
        if (end < 8 || size <= 0 || size > MAX_TEXT_BYTES || size > 18L * (end - 8)) return null;
        byte[] result = new byte[(int) size];
        int source = 8, target = 0;
        while (target < result.length) {
            checkInterrupted();
            if (source >= end) return null;
            int flags = bytes[source++] & 255;
            for (int bit = 0; bit < 8 && target < result.length; bit++, flags >>>= 1) {
                if ((flags & 1) != 0) {
                    if (source >= end) return null;
                    result[target++] = bytes[source++];
                } else {
                    if (end - source < 2) return null;
                    int token = (bytes[source] & 255) | ((bytes[source + 1] & 255) << 8);
                    source += 2;
                    int distance = token >>> 4, count = (token & 15) + 2;
                    if (distance == 0 || distance > target) return null;
                    for (int i = 0; i < count && target < result.length; i++) {
                        result[target] = result[target - distance];
                        target++;
                    }
                }
            }
        }
        return result;
    }

    private static long uint(byte[] bytes, int at) {
        return (bytes[at] & 255L) | ((bytes[at + 1] & 255L) << 8)
                | ((bytes[at + 2] & 255L) << 16) | ((bytes[at + 3] & 255L) << 24);
    }

    private static String guessText(byte[] bytes) throws IOException {
        Charset[] encodings = {StandardCharsets.UTF_16LE, SHIFT_JIS, StandardCharsets.UTF_8};
        for (Charset encoding : encodings) {
            if (encoding == StandardCharsets.UTF_16LE) {
                if (bytes.length < 2 || (bytes.length & 1) != 0) continue;
                if (bytes[0] != (byte) 255 || bytes[1] != (byte) 254) {
                    int zeros = 0;
                    for (int i = 1; i < bytes.length; i += 2) if (bytes[i] == 0) zeros++;
                    if (zeros * 10L < (bytes.length / 2L) * 3) continue;
                }
            }
            try {
                String text = decode(bytes, encoding);
                // Upstream removes the UTF-16LE BOM before probing Gameexe entries.
                if (text.startsWith("\uFEFF")) text = text.substring(1);
                int entries = 0;
                BufferedReader lines = new BufferedReader(new StringReader(text));
                for (int number = 0; number < 200; number++) {
                    String line = lines.readLine();
                    if (line == null) break;
                    if (line.trim().startsWith("#") && ++entries >= 3) return text;
                }
            } catch (CharacterCodingException ignored) {
                // Try the next encoding, just as the upstream text probe does.
            }
        }
        return null;
    }

    private static String decode(byte[] bytes, Charset encoding) throws CharacterCodingException {
        return encoding.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
    }

    private static Size screenSize(String text) throws IOException {
        Size result = null;
        BufferedReader lines = new BufferedReader(new StringReader(text));
        for (String raw; (raw = lines.readLine()) != null;) {
            checkInterrupted();
            String line = raw.trim();
            if (line.startsWith("\uFEFF")) line = line.substring(1).trim();
            if (line.isEmpty() || line.startsWith(";")) continue;
            if (line.startsWith("#")) line = line.substring(1).trim();
            int equal = line.indexOf('=');
            if (equal < 0) continue;
            String name = line.substring(0, equal).replaceAll("[\\t\\r\\n\\f ]", "");
            if (!asciiEquals(name, "SCREEN_SIZE")) continue;
            // The final definition is authoritative, even when its values are invalid.
            result = null;
            String[] values = comment(line.substring(equal + 1), ';').split(",", -1);
            int count = 0;
            String[] dimensions = new String[2];
            for (String item : values) {
                String value = item.trim();
                if (value.isEmpty()) continue;
                if (count < 2) dimensions[count] = value;
                count++;
            }
            if (count < 2) continue;
            try {
                int width = decimalDimension(dimensions[0]), height = decimalDimension(dimensions[1]);
                if (width > 0 && height > 0 && width <= MAX_STAGE_SIDE && height <= MAX_STAGE_SIDE
                        && (long) width * height <= MAX_STAGE_PIXELS) result = new Size(width, height);
            } catch (NumberFormatException ignored) {
                // A malformed final definition must not resurrect an earlier valid one.
            }
        }
        return result;
    }

    private static int decimalDimension(String value) {
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            value = value.substring(1, value.length() - 1).trim();
        }
        if (!value.matches("\\+?[0-9]+")) throw new NumberFormatException("invalid dimension");
        return Integer.parseInt(value);
    }

    private static String comment(String text, char marker) {
        char quote = 0;
        boolean escaped = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (escaped) { escaped = false; continue; }
            if (c == '\\' && quote == '"') { escaped = true; continue; }
            if (c == '"' || c == '\'') {
                if (quote == c) quote = 0;
                else if (quote == 0) quote = c;
            } else if (c == marker && quote == 0) return text.substring(0, i);
        }
        return text;
    }

    private static void checkInterrupted() throws IOException {
        if (Thread.currentThread().isInterrupted()) throw new IOException("cancelled");
    }
}
