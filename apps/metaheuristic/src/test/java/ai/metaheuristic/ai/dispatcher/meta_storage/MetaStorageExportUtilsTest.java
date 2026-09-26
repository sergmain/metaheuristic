/*
 * Metaheuristic, Copyright (C) 2017-2026, Innovation platforms, LLC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package ai.metaheuristic.ai.dispatcher.meta_storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static ai.metaheuristic.ai.dispatcher.meta_storage.MetaStorageExportUtils.*;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The naming and zipping rules of the meta-table download, as plain functions.
 *
 * <p>The export tests write to a REAL directory and read the REAL zip back with the JDK's own reader,
 * so they fail on what a filesystem actually does - including the case-insensitive collision, which
 * only bites on NTFS and APFS and is exactly the machine this runs on. Records are read through an
 * in-memory store behind the same function shape production passes, which returns what it holds for
 * the keys asked and nothing else - it behaves one way for every test.
 *
 * @author Serge
 */
@Execution(ExecutionMode.CONCURRENT)
public class MetaStorageExportUtilsTest {

    private record ZipContent(Set<String> dirs, Map<String, String> files) {}

    private static ZipContent readZip(Path zip) throws IOException {
        final Set<String> dirs = new HashSet<>();
        final Map<String, String> files = new HashMap<>();
        try (ZipFile zf = new ZipFile(zip.toFile(), UTF_8)) {
            for (Enumeration<? extends ZipEntry> e = zf.entries(); e.hasMoreElements(); ) {
                final ZipEntry entry = e.nextElement();
                if (entry.isDirectory()) {
                    dirs.add(entry.getName());
                }
                else {
                    files.put(entry.getName(), new String(zf.getInputStream(entry).readAllBytes(), UTF_8));
                }
            }
        }
        return new ZipContent(dirs, files);
    }

    /** What a store's select does: the records it holds for the keys asked, and no others. */
    private static Function<List<String>, List<MetaStorageData.Record>> readFrom(String type, Map<String, String> store) {
        return keys -> keys.stream()
                .filter(store::containsKey)
                .map(k -> new MetaStorageData.Record(type, k, store.get(k)))
                .toList();
    }

    // ---------- toFileName ----------

    @Test
    public void test_toFileName_keepsAPlainNameAsItIs() {
        assertEquals("drone-reqs_v1.2", toFileName("drone-reqs_v1.2"));
    }

    @Test
    public void test_toFileName_replacesPathSeparatorsSoAKeyCannotOpenASubdirectory() {
        assertEquals("a_b_c", toFileName("a/b\\c"));
    }

    @Test
    public void test_toFileName_replacesEveryCharacterWindowsForbids() {
        assertEquals("a" + "_".repeat(8) + "b", toFileName("a<>:\"|?*\u0001b"));
    }

    @Test
    public void test_toFileName_replacesWhitespaceIncludingTheInvisibleKinds() {
        assertEquals("a_b_c_d_e", toFileName("a b\tc\nd\u00A0e"));
    }

    @Test
    public void test_toFileName_neutralisesDotAndDotDot() {
        assertEquals("_", toFileName("."));
        assertEquals("__", toFileName(".."));
        assertEquals("___etc", toFileName("../etc"));
    }

    @Test
    public void test_toFileName_replacesALeadingDotSoTheFileIsNotHidden() {
        assertEquals("_hidden", toFileName(".hidden"));
        assertEquals("__x", toFileName("..x"));
    }

    @Test
    public void test_toFileName_replacesATrailingDotWindowsWouldDropButKeepsInnerDots() {
        assertEquals("name_", toFileName("name."));
        assertEquals("a.b.c", toFileName("a.b.c"));
    }

    @Test
    public void test_toFileName_turnsAnEmptyNameIntoAPlaceholder() {
        assertEquals("_", toFileName(""));
    }

    @Test
    public void test_toFileName_prefixesWindowsDeviceNamesInAnyCaseAndWithAnyExtension() {
        assertEquals("_CON", toFileName("CON"));
        assertEquals("_nul", toFileName("nul"));
        assertEquals("_Com1", toFileName("Com1"));
        assertEquals("_lpt9.log", toFileName("lpt9.log"));
    }

    @Test
    public void test_toFileName_leavesNamesThatOnlyStartLikeADevice() {
        assertEquals("CONSOLE", toFileName("CONSOLE"));
        assertEquals("com10", toFileName("com10"));
        assertEquals("auxiliary.json", toFileName("auxiliary.json"));
    }

    @Test
    public void test_toFileName_keepsLettersOfAnyScript() {
        assertEquals("требование-1", toFileName("требование-1"));
        assertEquals("要求-1", toFileName("要求-1"));
    }

    @Test
    public void test_toFileName_replacesASurrogatePairWithOneCharacterNotTwo() {
        assertEquals("a_b", toFileName("a\uD83D\uDE00b"));
    }

    @Test
    public void test_toFileName_boundsTheNameInUtf8BytesNotInChars() {
        final String ascii = toFileName("a".repeat(500));
        assertEquals(MAX_FILE_NAME_BYTES, ascii.getBytes(UTF_8).length);

        final String cyrillic = toFileName("ж".repeat(500));
        assertEquals(MAX_FILE_NAME_BYTES, cyrillic.getBytes(UTF_8).length);
        assertEquals(MAX_FILE_NAME_BYTES / 2, cyrillic.length());

        final String cjk = toFileName("中".repeat(500));
        assertEquals(MAX_FILE_NAME_BYTES, cjk.getBytes(UTF_8).length);
        assertEquals(MAX_FILE_NAME_BYTES / 3, cjk.length());
    }

    @Test
    public void test_toFileName_neverSplitsASurrogatePairWhenTruncating() {
        // U+1D49C MATHEMATICAL SCRIPT CAPITAL A is a letter outside the BMP - 4 bytes, 2 chars
        final String letter = "\uD835\uDC9C";
        assertEquals(letter.repeat(MAX_FILE_NAME_BYTES / 4), toFileName(letter.repeat(100)));
    }

    @Test
    public void test_toFileName_truncationCannotLeaveATrailingDot() {
        final String raw = "a".repeat(MAX_FILE_NAME_BYTES - 1) + ".tail";
        assertEquals("a".repeat(MAX_FILE_NAME_BYTES - 1) + "_", toFileName(raw));
    }

    // ---------- fileNamesByRecKey ----------

    @Test
    public void test_fileNames_useTheKeyItselfWhenItIsAlreadyAPlainName() {
        assertEquals(Map.of("DR-1", "DR-1.json", "DR-2", "DR-2.json"), fileNamesByRecKey(List.of("DR-1", "DR-2")));
    }

    @Test
    public void test_fileNames_separateKeysThatDifferOnlyInCase() {
        final Map<String, String> names = fileNamesByRecKey(List.of("Key", "key", "KEY"));
        assertEquals("Key.json", names.get("Key"));
        assertEquals("key-2.json", names.get("key"));
        assertEquals("KEY-3.json", names.get("KEY"));
    }

    @Test
    public void test_fileNames_separateKeysThatSanitiseToTheSameName() {
        final Map<String, String> names = fileNamesByRecKey(List.of("a/b", "a:b", "a?b"));
        assertEquals(List.of("a_b.json", "a_b-2.json", "a_b-3.json"), List.copyOf(names.values()));
    }

    @Test
    public void test_fileNames_aSuffixIsCheckedAgainstNamesAlreadyIssued() {
        // "X" cannot take "x.json", so it takes "X-2.json" - and then the real key "x-2" cannot take
        // its natural name either, because on a case-insensitive filesystem it is the same file
        final Map<String, String> names = fileNamesByRecKey(List.of("x", "X", "x-2"));
        assertEquals("x.json", names.get("x"));
        assertEquals("X-2.json", names.get("X"));
        assertEquals("x-2-2.json", names.get("x-2"));
    }

    @Test
    public void test_fileNames_areUniqueIgnoringCaseAcrossAHostileKeySet() {
        final List<String> keys = List.of("a", "A", "a.", "a ", "a/", "a\\", "a-2", "A-2",
                "..", ".", "", "CON", "con", "_CON", "a_", "a_-2");
        final Map<String, String> names = fileNamesByRecKey(keys);

        assertEquals(keys.size(), names.size(), "every key gets a name: " + names);
        final Set<String> lowered = names.values().stream().map(s -> s.toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
        assertEquals(keys.size(), lowered.size(), "names must be unique ignoring case: " + names);
        for (String name : names.values()) {
            assertTrue(name.endsWith(RECORD_FILE_EXTENSION), name);
            assertFalse(name.startsWith("."), name);
            assertFalse(name.contains("/") || name.contains("\\"), name);
        }
    }

    @Test
    public void test_fileNames_keepTheOrderOfTheKeys() {
        final List<String> keys = List.of("c", "a", "b");
        assertEquals(keys, List.copyOf(fileNamesByRecKey(keys).keySet()));
    }

    // ---------- exportToZip ----------

    @Test
    public void test_export_writesOneFilePerRecordInsideADirectoryNamedAfterTheTable(@TempDir Path workDir) throws IOException {
        final Map<String, String> store = new LinkedHashMap<>();
        store.put("DR-1", "{\"a\":1}");
        store.put("DR-2", "{\"b\":2}\n");
        store.put("ключ-3", "{\"текст\":\"значение\"}");

        final Export export = exportToZip(workDir, "drone-reqs", List.copyOf(store.keySet()), readFrom("drone-reqs", store), 500);

        assertEquals("drone-reqs.zip", export.zipFileName());
        assertEquals(3, export.records());
        final ZipContent zip = readZip(export.zip());
        assertEquals(Set.of("drone-reqs/"), zip.dirs());
        // bodies verbatim - the trailing newline and the non-latin text included
        assertEquals(Map.of(
                "drone-reqs/DR-1.json", "{\"a\":1}",
                "drone-reqs/DR-2.json", "{\"b\":2}\n",
                "drone-reqs/ключ-3.json", "{\"текст\":\"значение\"}"), zip.files());
    }

    @Test
    public void test_export_keepsATableNameFromLeavingTheWorkDirectory(@TempDir Path workDir) throws IOException {
        // a table written before name validation existed can be called anything
        final Path inner = Files.createDirectories(workDir.resolve("inner"));
        final Export export = exportToZip(inner, "../evil table", List.of("k"), readFrom("t", Map.of("k", "v")), 500);

        assertEquals("___evil_table.zip", export.zipFileName());
        assertEquals(inner, export.zip().getParent());
        assertEquals(Map.of("___evil_table/k.json", "v"), readZip(export.zip()).files());
        try (var siblings = Files.list(workDir)) {
            assertEquals(List.of(inner), siblings.toList(), "nothing may be written outside the work directory");
        }
    }

    @Test
    public void test_export_givesKeysThatDifferOnlyInCaseTwoFilesOnARealFilesystem(@TempDir Path workDir) throws IOException {
        final Map<String, String> store = new LinkedHashMap<>();
        store.put("Key", "upper");
        store.put("key", "lower");

        final Export export = exportToZip(workDir, "t", List.copyOf(store.keySet()), readFrom("t", store), 500);

        assertEquals(2, export.records());
        assertEquals(Map.of("t/Key.json", "upper", "t/key-2.json", "lower"), readZip(export.zip()).files());
    }

    @Test
    public void test_export_writesKeysLongerThanTheNameBound(@TempDir Path workDir) throws IOException {
        final Map<String, String> store = new LinkedHashMap<>();
        store.put("k".repeat(300), "latin");
        store.put("ж".repeat(300), "cyrillic");

        final Export export = exportToZip(workDir, "t", List.copyOf(store.keySet()), readFrom("t", store), 500);

        assertEquals(Map.of(
                "t/" + "k".repeat(MAX_FILE_NAME_BYTES) + RECORD_FILE_EXTENSION, "latin",
                "t/" + "ж".repeat(MAX_FILE_NAME_BYTES / 2) + RECORD_FILE_EXTENSION, "cyrillic"), readZip(export.zip()).files());
    }

    @Test
    public void test_export_readsInChunksWithoutLosingOrRepeatingARecord(@TempDir Path workDir) throws IOException {
        final Map<String, String> store = new LinkedHashMap<>();
        for (int i = 1; i <= 7; i++) {
            store.put("r-" + i, "body-" + i);
        }
        final Map<String, String> expected = new HashMap<>();
        store.forEach((k, v) -> expected.put("t/" + k + ".json", v));

        for (int chunkSize : new int[]{1, 2, 3, 6, 7, 8, 500}) {
            final Path dir = Files.createDirectories(workDir.resolve("chunk-" + chunkSize));
            final Export export = exportToZip(dir, "t", List.copyOf(store.keySet()), readFrom("t", store), chunkSize);
            assertEquals(7, export.records(), "chunkSize " + chunkSize);
            assertEquals(expected, readZip(export.zip()).files(), "chunkSize " + chunkSize);
        }
    }

    @Test
    public void test_export_skipsARecordDeletedBetweenListingAndReading(@TempDir Path workDir) throws IOException {
        // the key list says three, the store holds two - "b" went between the two reads
        final Export export = exportToZip(workDir, "t", List.of("a", "b", "c"),
                readFrom("t", Map.of("a", "A", "c", "C")), 500);

        assertEquals(2, export.records());
        assertEquals(Map.of("t/a.json", "A", "t/c.json", "C"), readZip(export.zip()).files());
    }

    @Test
    public void test_export_ofATableWithNoRecordsIsAZipHoldingOnlyTheDirectory(@TempDir Path workDir) throws IOException {
        final Export export = exportToZip(workDir, "t", List.of(), readFrom("t", Map.of()), 500);

        assertEquals(0, export.records());
        final ZipContent zip = readZip(export.zip());
        assertEquals(Set.of("t/"), zip.dirs());
        assertEquals(Map.of(), zip.files());
    }

    @Test
    public void test_export_writesTheZipBesideTheDirectoryNotInsideIt(@TempDir Path workDir) throws IOException {
        final Export export = exportToZip(workDir, "t", List.of("a"), readFrom("t", Map.of("a", "A")), 500);

        assertEquals(workDir.resolve("t.zip"), export.zip());
        final ZipContent zip = readZip(export.zip());
        assertEquals(Set.of("t/"), zip.dirs());
        assertEquals(Set.of("t/a.json"), zip.files().keySet(), "the archive must not contain itself");
    }

    @Test
    public void test_export_refusesAChunkSizeBelowOne(@TempDir Path workDir) {
        final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> exportToZip(workDir, "t", List.of("a"), readFrom("t", Map.of("a", "A")), 0));
        assertTrue(e.getMessage().startsWith("01.949.010 "), e.getMessage());
    }
}
