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

import ai.metaheuristic.ai.dispatcher.beans.LicenseArtifact;
import ai.metaheuristic.ai.dispatcher.repositories.LicenseArtifactRepository;
import ai.metaheuristic.api.data.license.LicenseArtifactParams;
import ai.metaheuristic.commons.json.license.LicenseArtifactParamsJsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The only writer of {@code MH_LICENSE_ARTIFACT}.
 *
 * <p>Verification does NOT happen here. A transaction is the wrong place to decide whether a
 * signature checks out, and the caller has already decided; this class only writes what it is
 * told, so the write stays short and the verify path stays pure.
 *
 * <p>Error code prefix: {@code 01.259.} (unique to this class).
 *
 * @author Serge
 */
@Service
@Profile("dispatcher")
@Slf4j
@RequiredArgsConstructor(onConstructor_ = {@Autowired})
public class LicenseArtifactTxService {

    private final LicenseArtifactRepository licenseArtifactRepository;

    /**
     * Add one license to the set, or revive its row if it had been removed. Idempotent: a token
     * that is already live changes nothing.
     *
     * @return the action actually taken, so the caller can report a duplicate as an outcome rather
     *         than an error
     */
    @Transactional
    public LicenseArtifactUtils.InstallAction install(String token, @Nullable Long installedByAccountId) {
        final String tokenHash = LicenseTokenHashUtils.hash(token);
        @Nullable final LicenseArtifact existing = licenseArtifactRepository.findByTokenHash(tokenHash);

        final LicenseArtifactUtils.InstallAction action =
                LicenseArtifactUtils.decideInstall(existing == null ? null : existing.deleted);

        switch (action) {
            case NOOP -> log.info("01.259.010 license is already installed, id: {}", existing==null ? null : existing.id);
            case REVIVE -> {
                //noinspection DataFlowIssue
                existing.deleted = false;
                // a revive IS an install, so the audit stamp moves: the row now records when this
                // license became live here, not when a since-removed copy of it once did.
                existing.createdOn = System.currentTimeMillis();
                existing.params = toJson(token, existing.createdOn, installedByAccountId);
                licenseArtifactRepository.save(existing);
                log.info("01.259.020 re-installed a previously removed license, id: {}", existing.id);
            }
            case CREATE -> {
                final long now = System.currentTimeMillis();
                final LicenseArtifact bean = new LicenseArtifact();
                bean.createdOn = now;
                bean.tokenHash = tokenHash;
                bean.deleted = false;
                bean.params = toJson(token, now, installedByAccountId);
                licenseArtifactRepository.save(bean);
                log.info("01.259.030 installed a license, id: {}", bean.id);
            }
        }
        return action;
    }

    /**
     * Remove one license from the set. Flips the flag rather than deleting the row — an offline
     * license cannot be revoked, so removing the artifact is the only retirement there is, and the
     * record of what was once installed is worth keeping.
     *
     * @return false when there was nothing live to remove
     */
    @Transactional
    public boolean remove(Long artifactId) {
        @Nullable final LicenseArtifact row = licenseArtifactRepository.findById(artifactId).orElse(null);
        if (row == null || row.deleted) {
            return false;
        }
        row.deleted = true;
        licenseArtifactRepository.save(row);
        log.info("01.259.040 removed a license, id: {}", artifactId);
        return true;
    }

    private static String toJson(String token, long installedOn, @Nullable Long installedByAccountId) {
        final LicenseArtifactParams params = new LicenseArtifactParams();
        params.token = token.strip();
        params.installedOn = installedOn;
        params.installedByAccountId = installedByAccountId;
        params.origin = LicenseArtifactParams.Origin.DB;
        return LicenseArtifactParamsJsonUtils.BASE_JSON_UTILS.toString(params);
    }
}
