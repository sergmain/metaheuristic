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

import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.dispatcher.beans.LicenseArtifact;
import ai.metaheuristic.ai.dispatcher.repositories.LicenseArtifactRepository;
import ai.metaheuristic.commons.json.license.LicenseArtifactParamsJsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Every license token installed on this dispatcher, from both places one can arrive.
 *
 * <p><b>Directory and database are ADDITIVE, not a precedence pair.</b> The old "file wins over
 * row" rule existed only because a single license could be active; under a SET there is nothing to
 * break a tie, so both are read and the union of the valid ones is the entitlement. Air-gapped and
 * read-only-filesystem installs favour the directory, container/cloud installs favour rows, and
 * supporting both costs nothing once neither has to win.
 *
 * <p>De-duplication is NOT done here — {@code SignedFileLicenseSource} drops repeats by token, so
 * the same license present in both places counts once wherever it came from.
 *
 * <p>An unreadable file is skipped with a warning rather than failing the read. One corrupt file
 * in a directory must not cost the installation every other license it holds; a token that cannot
 * be read simply grants nothing, which the verify path already models.
 *
 * <p>Error code prefix: {@code 01.256.} (unique to this class).
 *
 * @author Serge
 */
@Service
@Profile("dispatcher")
@Slf4j
@RequiredArgsConstructor(onConstructor_ = {@Autowired})
public class LicenseTokenSupplier {

    private final Globals globals;
    private final LicenseArtifactRepository licenseArtifactRepository;

    /** Directory first, then rows — a stable order so the admin breakdown does not shuffle. */
    public Collection<String> tokens() {
        final List<String> tokens = new ArrayList<>(directoryTokens());
        tokens.addAll(fromDatabase());
        return tokens;
    }

    /**
     * The license directory. Resolved lazily rather than bound as a property default because the
     * default is expressed relative to {@code mh.home}, which is not known at binding time.
     */
    public Path licenseDir() {
        return globals.license.dir!=null ? globals.license.dir : globals.getHome().resolve("config").resolve("license");
    }

    /**
     * Delegated to {@link LicenseDirScanUtils} so the directory decisions can be tested without a
     * Spring context: this class needs Globals and a repository, that one needs only a Path.
     *
     * <p>Public because the admin breakdown needs to know what is ON DISK, not merely what has no
     * DB row. The same licence can be installed through both channels, and until this was readable
     * the page could only infer the directory from the absence of a row - which reported nothing
     * for a token that had both.
     */
    public List<String> directoryTokens() {
        return LicenseDirScanUtils.scanDir(licenseDir());
    }

    private List<String> fromDatabase() {
        final List<String> tokens = new ArrayList<>();
        for (LicenseArtifact row : licenseArtifactRepository.findAllLive()) {
            try {
                final String token = LicenseArtifactParamsJsonUtils.BASE_JSON_UTILS.to(row.params).token;
                if (token!=null && !token.isBlank()) {
                    tokens.add(token.strip());
                }
            }
            catch (RuntimeException e) {
                log.warn("01.256.030 can't read the params of MH_LICENSE_ARTIFACT id: {}, skipping it: {}",
                        row.id, e.getMessage());
            }
        }
        return tokens;
    }
}
