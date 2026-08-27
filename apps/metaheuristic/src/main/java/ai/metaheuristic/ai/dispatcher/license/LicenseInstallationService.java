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
import ai.metaheuristic.ai.exceptions.LicenseInstallationMirrorException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;

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

    private static final AtomicReference<String> cached = new AtomicReference<>();

    /**
     * The installation id, minted on first call if this dispatcher has never had one.
     *
     * <p>Synchronized rather than compare-and-set: two threads racing here on a virgin database
     * would each mint a UUID and write a row, and the loser's id would be handed out to whoever
     * asked first. The window is one call at first boot, so the cost of serialising it is nil.
     */
    public String installationId() {
        return installationId(licenseInstallationTxService::getOrCreateInstallationId, id -> mirrorToFile(globals, id));
    }

    public static String installationId(Supplier<String> supplier, Consumer<String> consumer) {
        final String c = cached.get();
        if (c != null) {
            return c;
        }
        synchronized (cached) {
            final String again = cached.get();
            if (again != null) {
                return again;
            }
            final String id = supplier.get();
            consumer.accept(id);
            cached.set(id);
            return id;
        }
    }

    /**
     * The operator's copy of the id, so it can be read off the box without the UI.
     *
     * <p>❗ Load-bearing. A failure to write it raises
     * {@link ai.metaheuristic.ai.exceptions.LicenseInstallationMirrorException} rather than being
     * logged and stepped over, so the identity is not handed out unless the file beside it exists.
     *
     * <p>⚠️ The blast radius is wider than this method, because the throw leaves
     * {@link #installationId} before {@code cached.set(id)}: the cache stays empty, so every later
     * call re-enters and re-throws. An unwritable {@code mh.home}, or one that was never bound,
     * therefore takes the installation identity out of service permanently — and with it licence
     * installation, which passes the id to {@code LicenseTokenCodec.verify}, and entitlement
     * reporting, which reports it. A read-only {@code mh.home} is no longer a deployment this
     * method tolerates.
     *
     * <p>❗ It goes at the {@code {mh.home}} root, NOT under {@code {mh.home}/config}.
     * {@code config} is CONFIGURATION the operator supplies to the dispatcher, and deployments are
     * entitled to mount it read-only. Minting an identity into it is the dispatcher writing to its
     * own input: it fails exactly where the guarantee was supposed to hold, and on a writable box
     * it quietly puts generated state somewhere an operator may be copying between installations.
     * Since the paragraph above makes a failed write fatal, writing there would also hand a
     * read-only {@code config} the power to disable the identity outright. The root of
     * {@code mh.home} is where an operator looks first, and reading it off the box by hand is the
     * whole reason the file exists.
     *
     * <p>The move leaves any {@code config/installation-id.txt} from an earlier version untouched.
     * Deleting it would be a write into the directory this change exists to stop writing to, and
     * the value in it is not wrong — the id never changes — merely orphaned.
     */
    static void mirrorToFile(Globals globals, String id) {
        try {
            final Path dir = globals.getHome();
            final Path file = dir.resolve(INSTALLATION_ID_FILE);
            final String current = Files.exists(file) ? Files.readString(file, StandardCharsets.UTF_8) : null;
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
            throw new LicenseInstallationMirrorException(
                    "01.255.020 can't mirror the installation id to a file: " + e.getMessage(), e);
        }
    }
}
