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

package ai.metaheuristic.ai.dispatcher.license;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * The license-directory scanner: every {@code *.jws} in one directory, read as a compact JWS token.
 *
 * <p>Static and Spring-free on purpose. {@link LicenseTokenSupplier} needs {@code Globals} and a
 * repository, so anything left inside it can only be exercised with a Spring context; the part
 * that actually decides what the directory yields needs nothing but a {@link Path} and is
 * therefore testable against a temp dir.
 *
 * <p><b>Nothing here is fatal.</b> A missing directory, a directory that is really a file, an
 * unreadable file, a blank file — each yields "no token from this" and never an exception. That is
 * the same rule the verify path already follows for a bad token: one corrupt file in a directory
 * must not cost the installation every other license it holds. A directory that cannot be listed
 * is the one case worth a warning, because it is the case where licenses the admin believes are
 * installed silently are not.
 *
 * <p>Depth 1 only, and regular files only. A subdirectory that happens to be named
 * {@code something.jws} is not a license and is skipped without a warning rather than being read
 * and failing.
 *
 * <p>Order is by file name so the admin breakdown does not shuffle between reads.
 *
 * <p>Error code prefix: {@code 01.258.} (unique to this class).
 *
 * @author Serge
 */
@Slf4j
public class LicenseDirScanUtils {

    public static final String JWS_SUFFIX = ".jws";

    private LicenseDirScanUtils() {
    }

    /**
     * Every readable, non-blank {@code *.jws} in {@code dir}, in file-name order. Never throws.
     *
     * <p>Each file taken is named in the log. Downstream a license is identified by its licensee
     * and its state, never by where it came from - the token set is de-duplicated across the
     * directory and the database before verification, so no later message can say which FILE a
     * verdict belongs to. This is the only point at which the mapping still exists.
     */
    public static List<String> scanDir(Path dir) {
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        final List<String> tokens = new ArrayList<>();
        try (Stream<Path> files = Files.list(dir)) {
            files.filter(LicenseDirScanUtils::isLicenseFile)
                    .sorted()
                    .forEach(p -> readQuietly(p, tokens));
        }
        catch (IOException | RuntimeException e) {
            log.warn("01.258.010 can't list the license dir {}, no license is read from it: {}", dir, e.getMessage());
            return List.of();
        }
        if (tokens.isEmpty()) {
            log.info("No *{} file was read from the license dir {}", JWS_SUFFIX, dir);
        }
        else {
            log.info("Read {} license file(s) from {}", tokens.size(), dir);
        }
        return tokens;
    }

    private static boolean isLicenseFile(Path p) {
        return p.getFileName().toString().endsWith(JWS_SUFFIX) && Files.isRegularFile(p);
    }

    /**
     * A compact JWS carries no whitespace, so the content is stripped: a file that picked up a
     * trailing newline from an editor is the same license as one that did not, and treating them
     * as different would produce two rows for one grant.
     */
    private static void readQuietly(Path file, List<String> acc) {
        try {
            final String token = Files.readString(file, StandardCharsets.UTF_8).strip();
            if (token.isBlank()) {
                log.warn("01.258.030 the license file {} is empty, skipping it", file);
                return;
            }
            acc.add(token);
            log.info("License file taken: {}", file);
        }
        catch (IOException | RuntimeException e) {
            log.warn("01.258.020 can't read the license file {}, skipping it: {}", file, e.getMessage());
        }
    }
}
