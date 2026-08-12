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
import ai.metaheuristic.commons.spi.license.DeploymentValues;
import ai.metaheuristic.commons.spi.license.LicenseAggregate;
import ai.metaheuristic.commons.spi.license.SignedFileLicenseSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Projects the license aggregate into what the admin page renders.
 *
 * <p>Read-only and derived: it decides nothing about validity. Every state, every grant and the
 * union itself come from the verify core; this class adds only the things the core has no business
 * knowing — which row a token came from, who installed it, and which backend is answering.
 *
 * <p><b>{@link LicenseType} is derived from the active bean, never guessed.</b> This service is
 * bound to {@code internal-lm}, so it reports INTERNAL structurally rather than by inspecting
 * config that could disagree with reality.
 *
 * <p>Error code prefix: {@code 01.257.} (unique to this class).
 *
 * @author Serge
 */
@Service
@Profile("dispatcher & internal-lm")
@Slf4j
@RequiredArgsConstructor(onConstructor_ = {@Autowired})
public class LicenseInfoService {

    private final SignedFileLicenseSource licenseSource;
    private final LicenseArtifactRepository licenseArtifactRepository;
    private final LicenseInstallationService licenseInstallationService;
    private final DeploymentValuesResolverHolder deploymentValuesResolverHolder;

    public LicenseInfoData.LicenseInfo info() {
        final LicenseAggregate aggregate = licenseSource.currentResult();
        final DeploymentValues deployment = deploymentValuesResolverHolder.current();

        return new LicenseInfoData.LicenseInfo(
                effective(aggregate, deployment),
                LicenseInfoUtils.breakdown(aggregate.licenses(), liveRowsByTokenHash()));
    }

    private LicenseInfoData.EffectiveEntitlement effective(LicenseAggregate aggregate, DeploymentValues deployment) {
        return new LicenseInfoData.EffectiveEntitlement(
                LicenseType.INTERNAL,
                aggregate.entitlements().valid(),
                aggregate.state(),
                sorted(aggregate.capabilities()),
                sorted(aggregate.databases()),
                sorted(aggregate.storages()),
                LicenseInfoUtils.toMillis(aggregate.expiresAt()),
                licenseInstallationService.installationId(),
                deployment.database(),
                deployment.storage());
    }

    private Map<String, LicenseInfoUtils.RowInfo> liveRowsByTokenHash() {
        final Map<String, LicenseInfoUtils.RowInfo> byHash = new HashMap<>();
        for (LicenseArtifact row : licenseArtifactRepository.findAllLive()) {
            @Nullable final LicenseArtifactParams params = toParams(row);
            byHash.put(row.tokenHash, new LicenseInfoUtils.RowInfo(
                    row.id,
                    params==null ? LicenseArtifactParams.Origin.DB : params.origin,
                    row.createdOn));
        }
        return byHash;
    }

    @Nullable
    private LicenseArtifactParams toParams(LicenseArtifact row) {
        try {
            return LicenseArtifactParamsJsonUtils.BASE_JSON_UTILS.to(row.params);
        }
        catch (RuntimeException e) {
            log.warn("01.257.010 can't read the params of MH_LICENSE_ARTIFACT id: {}: {}", row.id, e.getMessage());
            return null;
        }
    }

    private static List<String> sorted(Collection<String> values) {
        return values.stream().sorted().toList();
    }
}
