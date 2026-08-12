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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The installation identity every other part of the license manager asks for.
 *
 * <p>Non-transactional orchestrator: the write lives in {@link LicenseInstallationTxService}, the
 * decisions live in {@link LicenseInstallationUtils}, and what is left here is caching and file IO.
 *
 * <p>The value is cached for the life of the process because it cannot change: it is minted once
 * and never rewritten. That matters because the verify path consults it on every refresh, and it
 * is the one input there that must never become a database round-trip.
 *
 * <p>Error code prefix: {@code 01.255.} (unique to this class).
 *
 * @author Serge
 */
@Service
@Profile("dispatcher")
@Slf4j
@RequiredArgsConstructor(onConstructor_ = {@Autowired})
public class LicenseInstallationService {

    public static final String INSTALLATION_ID_FILE = "installation-id.txt";

    private final Globals globals;
    private final LicenseInstallationTxService licenseInstallationTxService;

    private final AtomicReference<String> cached = new AtomicReference<>();

    /**
     * The installation id, minted on first call if this dispatcher has never had one.
     *
     * <p>Synchronized rather than compare-and-set: two threads racing here on a virgin database
     * would each mint a UUID and write a row, and the loser's id would be handed out to whoever
     * asked first. The window is one call at first boot, so the cost of serialising it is nil.
     */
    public String installationId() {
        final String c = cached.get();
        if (c != null) {
            return c;
        }
        synchronized (this) {
            final String again = cached.get();
            if (again != null) {
                return again;
            }
            final String id = licenseInstallationTxService.getOrCreateInstallationId();
            mirrorToFile(id);
            cached.set(id);
            return id;
        }
    }

    /**
     * Best-effort copy for the operator, so the id can be read off the box without the UI.
     *
     * <p>Never load-bearing. A read-only filesystem is a supported deployment (air-gapped,
     * container images), so a failure here is logged and boot continues on the database value —
     * refusing to start because a convenience file could not be written would take out a
     * dispatcher over something that grants nothing.
     */
    private void mirrorToFile(String id) {
        try {
            final Path dir = globals.getHome().resolve("config");
            final Path file = dir.resolve(INSTALLATION_ID_FILE);
            @Nullable final String current = Files.exists(file) ? Files.readString(file, StandardCharsets.UTF_8) : null;
            if (LicenseInstallationUtils.decideMirror(id, current) == LicenseInstallationUtils.MirrorAction.LEAVE) {
                return;
            }
            if (current != null) {
                log.warn("01.255.010 {} disagrees with MH_LICENSE_INSTALLATION; the database wins and the file is rewritten",
                        file);
            }
            Files.createDirectories(dir);
            Files.writeString(file, id, StandardCharsets.UTF_8);
        }
        catch (IOException | RuntimeException e) {
            log.warn("01.255.020 can't mirror the installation id to a file, continuing on the database value: "
                    + e.getMessage());
        }
    }
}
