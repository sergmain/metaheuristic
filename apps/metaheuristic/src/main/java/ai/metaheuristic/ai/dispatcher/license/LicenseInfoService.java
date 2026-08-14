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
import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.commons.spi.license.SignedFileLicenseSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
/**
 * ❗ Bound to the presence of an OFFLINE backend, not to a single profile name.
 *
 * <p>This is the licence-ADMIN surface: install a signed file, remove one, list what is installed.
 * None of that exists under an external authority — on {@code aws-lm} the entitlement comes from
 * AWS and there is nothing to install — and the bean injects the concrete
 * {@code SignedFileLicenseSource}, which only the offline backends declare. Left on plain
 * {@code @Profile("dispatcher")} it would fail context startup under {@code aws-lm} with a missing
 * bean.
 *
 * <p>{@code @ConditionalOnBean} is used rather than a profile expression because the set of
 * offline backends is OPEN — {@code internal-lm} plus each test harness — and a profile expression
 * would have to be edited every time one is added, which is exactly the negative-list problem that
 * backend selection was moved away from.
 *
 * <p>UPDATE: the {@code @ConditionalOnBean} reasoning above is superseded. That annotation is
 * only ordering-safe inside auto-configuration; on a component-scanned bean it races the
 * configuration that declares the source. The open-set concern it was chosen for is now met by
 * naming the backend family once in {@link ai.metaheuristic.ai.Consts#SIGNED_FILE_LM_PROFILE}.
 */
@Profile(Consts.SIGNED_FILE_LM_PROFILE)
@Slf4j
@RequiredArgsConstructor(onConstructor_ = {@Autowired})
public class LicenseInfoService {

    private final SignedFileLicenseSource licenseSource;
    private final LicenseArtifactRepository licenseArtifactRepository;
    private final LicenseInstallationService licenseInstallationService;
    private final DeploymentValuesResolverHolder deploymentValuesResolverHolder;
    private final LicenseTokenSupplier licenseTokenSupplier;

    public LicenseInfoData.LicenseInfo info() {
        final LicenseAggregate aggregate = licenseSource.currentResult();
        final DeploymentValues deployment = deploymentValuesResolverHolder.current();

        return new LicenseInfoData.LicenseInfo(
                effective(aggregate, deployment),
                LicenseInfoUtils.breakdown(
                        aggregate.licenses(), liveRowsByTokenHash(), directoryTokenHashes()));
    }

    /**
     * The effective capability set alone. Reads the aggregate and nothing else — no per-licence
     * breakdown, so no MH_LICENSE_ARTIFACT query — because this is called on navigation rather than
     * on an admin opening a page.
     */
    public LicenseInfoData.Capabilities capabilities() {
        final LicenseAggregate aggregate = licenseSource.currentResult();
        return new LicenseInfoData.Capabilities(
                aggregate.entitlements().valid(), sorted(aggregate.capabilities()));
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

    /**
     * What the licence DIRECTORY is holding right now, by token hash.
     *
     * <p>The disk is re-read rather than inferred from the aggregate: the aggregate carries
     * de-duplicated tokens and cannot say through which channel any of them arrived. This is a
     * directory listing of a handful of small files behind an admin page, so reading it per call is
     * cheaper than any way of keeping a copy of it honest.
     */
    private Set<String> directoryTokenHashes() {
        final Set<String> hashes = new HashSet<>();
        for (String token : licenseTokenSupplier.directoryTokens()) {
            hashes.add(LicenseTokenHashUtils.hash(token));
        }
        return hashes;
    }

    private Map<String, LicenseInfoUtils.RowInfo> liveRowsByTokenHash() {
        final Map<String, LicenseInfoUtils.RowInfo> byHash = new HashMap<>();
        for (LicenseArtifact row : licenseArtifactRepository.findAllLive()) {
            final LicenseArtifactParams params = toParams(row);
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
