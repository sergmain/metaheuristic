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

import ai.metaheuristic.commons.utils.ZipUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * One meta table as a zip: a directory named after the table, one file per record holding that
 * record's body verbatim.
 *
 * <p>❗ Neither name can reach the filesystem as it is. A record key is a free-form string - nothing
 * validates it, and real keys carry '@', ':' and '/' - and a table name is validated only on the write
 * path ({@link MetaStorageNameUtils}), so a table written before that check existed can be called
 * anything. A '/' in a key is a subdirectory, ".." is a way out of the export directory, a trailing
 * dot is silently dropped by Windows, and "CON" is a device there. {@link #toFileName} maps each of
 * those to a plain name.
 *
 * <p>❗ That mapping is lossy, so uniqueness is enforced separately by {@link #fileNamesByRecKey} -
 * and case-INsensitively. Two keys that differ only in case are two records, but on NTFS and APFS
 * they are one file, and the second write would silently replace the first. Resolving collisions
 * against the lower-cased name is what keeps an export taken on such a filesystem complete.
 *
 * <p>Spring-free and store-free: records arrive through a function, so the same code serves both
 * stores and runs in a plain JUnit test against a real directory.
 *
 * <p>Error code prefix: {@code 01.949.} (unique to this class).
 *
 * @author Serge
 */
public final class MetaStorageExportUtils {

    /** Bodies are JSON by convention - MH never checks - so the extension names what they are meant to be. */
    public static final String RECORD_FILE_EXTENSION = ".json";

    public static final String ZIP_EXTENSION = ".zip";

    /**
     * Upper bound on a name's UTF-8 length, before a collision suffix and the extension are added.
     *
     * <p>⚠️ Bytes, not characters: ext4 caps a file name at 255 BYTES, and one Cyrillic character is
     * two of them, one CJK character three. A bound in characters would let a Cyrillic or CJK key
     * produce a name the filesystem refuses. 120 leaves room for "-NNNN" and ".json" with margin.
     */
    public static final int MAX_FILE_NAME_BYTES = 120;

    public static final char REPLACEMENT = '_';

    /** Device names Windows reserves in every directory, with or without an extension. Lower-case. */
    private static final Set<String> WINDOWS_RESERVED_NAMES = Set.of(
            "con", "prn", "aux", "nul",
            "com1", "com2", "com3", "com4", "com5", "com6", "com7", "com8", "com9",
            "lpt1", "lpt2", "lpt3", "lpt4", "lpt5", "lpt6", "lpt7", "lpt8", "lpt9");

    /**
     * @param zip          the archive, written beside the table directory
     * @param zipFileName  the name the archive is offered to the browser under
     * @param records      how many record files went into it - fewer than the keys listed when a
     *                     record was deleted between listing and reading, which is not an error
     */
    public record Export(Path zip, String zipFileName, int records) {}

    private MetaStorageExportUtils() {
    }

    /**
     * A name that is safe as ONE path segment on Linux, Windows and macOS, and inside a zip.
     *
     * <p>Letters and digits of any script are kept, as are '-', '_' and '.'; every other code point
     * becomes {@link #REPLACEMENT} - one replacement per code point, so an emoji is one '_', not two.
     * Leading and trailing dots are replaced too, an empty result becomes a single replacement, and a
     * Windows device name gets the replacement prefixed.
     */
    public static String toFileName(String raw) {
        final StringBuilder sb = new StringBuilder(Math.min(raw.length(), MAX_FILE_NAME_BYTES));
        int bytes = 0;
        for (int i = 0; i < raw.length(); ) {
            final int cp = raw.codePointAt(i);
            i += Character.charCount(cp);
            final int mapped = isSafe(cp) ? cp : REPLACEMENT;
            final int length = utf8Length(mapped);
            // truncation is by whole code points, so a surrogate pair is never split
            if (bytes + length > MAX_FILE_NAME_BYTES) {
                break;
            }
            sb.appendCodePoint(mapped);
            bytes += length;
        }
        // Checked AFTER truncation: cutting "aaa.tail" at the dot would otherwise leave a trailing one.
        // A leading dot hides the file on unix, and ".." is the parent directory.
        for (int i = 0; i < sb.length() && sb.charAt(i)=='.'; i++) {
            sb.setCharAt(i, REPLACEMENT);
        }
        // Windows drops a trailing dot silently, so "a." and "a" would land on one file.
        for (int i = sb.length() - 1; i >= 0 && sb.charAt(i)=='.'; i--) {
            sb.setCharAt(i, REPLACEMENT);
        }
        if (sb.isEmpty()) {
            return String.valueOf(REPLACEMENT);
        }
        final String name = sb.toString();
        return isWindowsReserved(name) ? REPLACEMENT + name : name;
    }

    /**
     * A file name per record key, unique ignoring case, in the order the keys were given.
     *
     * <p>A key whose sanitised name is already taken gets "-2", "-3", ... before the extension. The
     * suffixed candidate is checked against every name issued so far, so a suffix can never land on a
     * name another key already holds. Deterministic for a given key list - and the stores list keys
     * ordered by recKey, so the same table exports under the same names every time.
     */
    public static Map<String, String> fileNamesByRecKey(List<String> recKeys) {
        final Map<String, String> result = new LinkedHashMap<>();
        final Set<String> taken = new HashSet<>();
        for (String recKey : recKeys) {
            if (result.containsKey(recKey)) {
                continue;
            }
            final String base = toFileName(recKey);
            String candidate = base + RECORD_FILE_EXTENSION;
            for (int n = 2; !taken.add(candidate.toLowerCase(Locale.ROOT)); n++) {
                candidate = base + "-" + n + RECORD_FILE_EXTENSION;
            }
            result.put(recKey, candidate);
        }
        return result;
    }

    /**
     * Writes the table into {@code workDir} and zips it.
     *
     * <p>Bodies are read {@code chunkSize} keys at a time and written out before the next chunk is
     * read, so a table of any size holds at most one chunk of bodies in memory.
     *
     * @param recordsByKeys the store's read for a named subset of keys. A key it does not return -
     *                      a record deleted after the key list was taken - is skipped.
     */
    public static Export exportToZip(
            Path workDir, String metaTable, List<String> recKeys,
            Function<List<String>, List<MetaStorageData.Record>> recordsByKeys, int chunkSize) throws IOException {

        if (chunkSize < 1) {
            throw new IllegalArgumentException("01.949.010 chunkSize must be positive, was: " + chunkSize);
        }
        final String dirName = toFileName(metaTable);
        final Path tableDir = workDir.resolve(dirName);
        Files.createDirectories(tableDir);

        final Map<String, String> fileNames = fileNamesByRecKey(recKeys);
        int written = 0;
        for (int from = 0; from < recKeys.size(); from += chunkSize) {
            final List<String> chunk = recKeys.subList(from, Math.min(from + chunkSize, recKeys.size()));
            for (MetaStorageData.Record r : recordsByKeys.apply(chunk)) {
                final String fileName = fileNames.get(r.recKey());
                if (fileName==null) {
                    throw new IllegalStateException("01.949.020 A record came back for a key that was not asked for, "
                            + "meta table: '" + metaTable + "', recKey: '" + r.recKey() + "'");
                }
                Files.writeString(tableDir.resolve(fileName), r.body(), StandardCharsets.UTF_8);
                written++;
            }
        }

        // ❗ BESIDE the table directory, never inside it: ZipUtils walks the directory while it writes,
        // and a zip placed inside would be archived into itself.
        final String zipFileName = dirName + ZIP_EXTENSION;
        final Path zip = workDir.resolve(zipFileName);
        ZipUtils.createZip(tableDir, zip);
        return new Export(zip, zipFileName, written);
    }

    private static boolean isSafe(int cp) {
        return Character.isLetterOrDigit(cp) || cp=='-' || cp=='_' || cp=='.';
    }

    private static int utf8Length(int cp) {
        if (cp < 0x80) {
            return 1;
        }
        if (cp < 0x800) {
            return 2;
        }
        return cp < 0x10000 ? 3 : 4;
    }

    /** "CON" and "con.json" alike: Windows reserves the name whatever follows the first dot. */
    private static boolean isWindowsReserved(String name) {
        final int dot = name.indexOf('.');
        final String base = dot==-1 ? name : name.substring(0, dot);
        return WINDOWS_RESERVED_NAMES.contains(base.toLowerCase(Locale.ROOT));
    }
}
