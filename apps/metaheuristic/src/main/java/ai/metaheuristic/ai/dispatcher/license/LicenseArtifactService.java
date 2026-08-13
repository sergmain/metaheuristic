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

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.dispatcher.signal_bus.ScopeRef;
import ai.metaheuristic.ai.dispatcher.signal_bus.SignalBus;
import ai.metaheuristic.ai.dispatcher.signal_bus.SignalKind;
import ai.metaheuristic.api.EnumsApi;

import java.util.Map;
import ai.metaheuristic.api.data.OperationStatusRest;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.spi.license.LicenseTokenCodec;
import ai.metaheuristic.commons.spi.license.LicenseVerificationKeys;
import ai.metaheuristic.commons.spi.license.LicenseVerificationResult;
import ai.metaheuristic.commons.spi.license.SignedFileLicenseSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;

/**
 * Add and remove licenses. Non-transactional orchestrator: the write lives in
 * {@link LicenseArtifactTxService} and the decisions in {@link LicenseArtifactUtils}.
 *
 * <p><b>An upload is verified BEFORE anything is written.</b> Persisting first and discovering
 * later would leave junk in the set that the admin then has to find and remove, and the breakdown
 * would list a row that is not a license at all. The verify used here is the same pure codec the
 * runtime uses, against the same compiled-in key, so an install cannot accept anything the
 * runtime would later refuse to parse.
 *
 * <p>The cache is dropped after every successful write. The TTL exists to bound how long validity
 * lags the clock, not how long it lags an admin: without this a fresh upload would stay invisible
 * for up to a minute and look like a failure.
 *
 * <p>Error code prefix: {@code 01.262.} (unique to this class).
 *
 * @author Serge
 */
@Service
@Profile("dispatcher")
@Slf4j
@RequiredArgsConstructor(onConstructor_ = {@Autowired})
public class LicenseArtifactService {

    private final LicenseArtifactTxService licenseArtifactTxService;
    private final SignalBus signalBus;
    private final LicenseInstallationService licenseInstallationService;
    private final SignedFileLicenseSource licenseSource;

    public OperationStatusRest install(@Nullable String token, @Nullable Long installedByAccountId) {
        if (S.b(token)) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "01.262.010 the license is empty");
        }
        final String trimmed = token.strip();

        final LicenseVerificationResult verified = LicenseTokenCodec.verify(
                trimmed, LicenseVerificationKeys::byKid,
                Instant.now(Clock.systemUTC()), licenseInstallationService.installationId());

        if (!LicenseArtifactUtils.isInstallable(verified.state())) {
            // nothing is written: the set is left exactly as it was.
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "01.262.020 this is not a valid license, state: " + verified.state());
        }

        final LicenseArtifactUtils.InstallAction action = licenseArtifactTxService.install(trimmed, installedByAccountId);
        licenseSource.invalidate();
        announce("installed");

        return switch (action) {
            // a repeat is an outcome, not a fault — re-installing a license you already hold is
            // not a mistake worth reporting as an error.
            case NOOP -> new OperationStatusRest(EnumsApi.OperationStatus.OK,
                    "this license is already installed", null);
            case REVIVE -> new OperationStatusRest(EnumsApi.OperationStatus.OK,
                    "a previously removed license was re-installed, state: " + verified.state(), null);
            case CREATE -> new OperationStatusRest(EnumsApi.OperationStatus.OK,
                    "the license was installed, state: " + verified.state(), null);
        };
    }

    /**
     * Tell any connected UI that the entitlement changed, so it re-reads instead of showing a user
     * a licence they already installed as still missing.
     *
     * <p>❗ UI-notification only. Nothing server-side listens for this, and nothing should — the
     * runtime reads the licence itself on every gate.
     */
    private void announce(String what) {
        try {
            signalBus.put(SignalKind.LICENSE_STATE, what, new ScopeRef(Consts.ID_1),
                    Map.of("change", what), true);
        }
        catch (RuntimeException e) {
            // a UI notification is not worth failing an install that already succeeded.
            log.warn("01.262.050 couldn't publish the licence-state signal: {}", e.getMessage());
        }
    }

    public OperationStatusRest remove(@Nullable Long artifactId) {
        if (artifactId == null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "01.262.030 the license id is missing");
        }
        if (!licenseArtifactTxService.remove(artifactId)) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "01.262.040 there is no installed license with id: " + artifactId);
        }
        licenseSource.invalidate();
        announce("removed");
        return OperationStatusRest.OPERATION_STATUS_OK;
    }
}
