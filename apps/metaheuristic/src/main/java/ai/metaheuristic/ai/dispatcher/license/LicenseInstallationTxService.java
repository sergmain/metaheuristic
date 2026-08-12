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

import ai.metaheuristic.ai.dispatcher.beans.LicenseInstallation;
import ai.metaheuristic.ai.dispatcher.repositories.LicenseInstallationRepository;
import ai.metaheuristic.api.data.license.LicenseInstallationParams;
import ai.metaheuristic.commons.json.license.LicenseInstallationParamsJsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * The only writer of {@code MH_LICENSE_INSTALLATION}.
 *
 * <p>Error code prefix: {@code 01.254.} (unique to this class).
 *
 * @author Serge
 */
@Service
@Profile("dispatcher")
@Slf4j
@RequiredArgsConstructor(onConstructor_ = {@Autowired})
public class LicenseInstallationTxService {

    private final LicenseInstallationRepository licenseInstallationRepository;

    /**
     * Read the installation id, minting it on first boot. Idempotent: a second call returns the
     * same value and writes nothing.
     */
    @Transactional
    public String getOrCreateInstallationId() {
        final List<LicenseInstallation> rows = licenseInstallationRepository.findAllOrderByIdAsc();
        @Nullable final LicenseInstallation existing = LicenseInstallationUtils.pickAuthoritative(rows);
        if (existing != null) {
            if (rows.size() > 1) {
                log.warn("01.254.010 MH_LICENSE_INSTALLATION holds {} rows, expected 1; using the oldest, id: {}",
                        rows.size(), existing.id);
            }
            return toParams(existing.params).installationId;
        }

        final LicenseInstallationParams params = new LicenseInstallationParams();
        params.installationId = UUID.randomUUID().toString();
        params.createdOn = System.currentTimeMillis();

        final LicenseInstallation bean = new LicenseInstallation();
        bean.createdOn = params.createdOn;
        bean.params = LicenseInstallationParamsJsonUtils.BASE_JSON_UTILS.toString(params);
        licenseInstallationRepository.save(bean);

        log.info("01.254.020 minted the installation id for this dispatcher: {}", params.installationId);
        return params.installationId;
    }

    private static LicenseInstallationParams toParams(String json) {
        return LicenseInstallationParamsJsonUtils.BASE_JSON_UTILS.to(json);
    }
}
