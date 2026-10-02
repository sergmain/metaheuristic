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

package ai.metaheuristic.ai.dispatcher.vault;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.dispatcher.data.VaultData;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Function;

import static ai.metaheuristic.ai.dispatcher.vault.VaultBootUnlockUtils.Status.UNLOCKED;
import static ai.metaheuristic.ai.dispatcher.vault.VaultBootUnlockUtils.Status.UNLOCK_FAILED;

/**
 * Unlocks the management company's Key Vault at Dispatcher start, and produces the value that makes it
 * possible.
 *
 * <p>Config: {@code mh.dispatcher.vault.boot-unlock.encrypted-passphrase} holds the Vault passphrase
 * encrypted with the KEK; {@code mh.dispatcher.vault.boot-unlock.kek-env} names the OS environment variable
 * holding the KEK. Nothing configured = the Vault stays locked after a restart, as before.
 *
 * <p>❗ Runs in {@code @PostConstruct}, i.e. before the web server accepts Processor requests, so there is no
 * window in which a Processor asks for a sealed secret while the management Vault is still locked.
 *
 * <p>❗ Never creates a Vault: a company without a persisted Vault is skipped, otherwise
 * {@link VaultService#unlock(long, String)} would create one whose master passphrase is the machine-held one.
 *
 * <p>Where the encrypted passphrase comes from: {@code ${mh.home}/vault-boot-unlock.txt}, which
 * {@link #bootUnlockValue} writes when the admin presses "Auto-unlock at restart" - nothing goes into
 * application.properties by hand. The property {@code mh.dispatcher.vault.boot-unlock.encrypted-passphrase}
 * is only a fallback when that file is absent. See {@link VaultBootUnlockFileUtils}.
 *
 * <p>Error code prefix: {@code 01.672.} (unique to this class).
 *
 * @author Sergio Lissner
 * Date: 10/1/2026
 */
@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class VaultBootUnlockService {

    public static final String PROPERTY_NAME = "mh.dispatcher.vault.boot-unlock.encrypted-passphrase";

    private final Globals globals;
    private final VaultService vaultService;

    @PostConstruct
    public void init() {
        try {
            final Globals.BootUnlock cfg = globals.dispatcher.vault.bootUnlock;
            final Path home = globals.getHome();
            // what boot-unlock is about to work with; presence only, never a value
            log.info("01.672.170 Key Vault boot-unlock inputs: env {}: {}; {}; property {}: {}",
                cfg.kekEnv, VaultBootUnlockUtils.describeKek(System.getenv(cfg.kekEnv)),
                VaultBootUnlockFileUtils.describeFile(home),
                PROPERTY_NAME, cfg.encryptedPassphrase == null || cfg.encryptedPassphrase.isBlank() ? "blank" : "set");
            bootUnlock(Consts.MANAGEMENT_COMPANY_ID,
                VaultBootUnlockFileUtils.readEncryptedPassphrase(home, cfg.encryptedPassphrase),
                cfg.kekEnv, System::getenv);
        }
        catch (Throwable th) {
            log.error("01.672.010 Key Vault boot-unlock failed unexpectedly, the Vault stays locked", th);
        }
    }

    /**
     * @param companyUniqueId company whose Vault is unlocked; production passes the management company
     * @param cfg             boot-unlock config
     * @param encryptedPassphrase Base64 ciphertext of the passphrase, from {@code ${mh.home}/vault-boot-unlock.txt}
     *                        or the fallback property; null = not configured
     * @param kekEnv          name of the environment variable holding the KEK
     * @param env             environment lookup; production passes {@code System::getenv}
     */
    public VaultBootUnlockUtils.Status bootUnlock(
            long companyUniqueId, @Nullable String encryptedPassphrase, String kekEnv, Function<String, @Nullable String> env) {
        final VaultBootUnlockUtils.BootPassphrase bp = VaultBootUnlockUtils.resolveBootPassphrase(
            encryptedPassphrase, env.apply(kekEnv), companyUniqueId, vaultService::hasVault);

        final VaultBootUnlockUtils.Status status = switch (bp.status()) {
            case READY -> vaultService.unlock(companyUniqueId, Objects.requireNonNull(bp.passphrase())).opened ? UNLOCKED : UNLOCK_FAILED;
            default -> bp.status();
        };

        switch (status) {
            case NOT_CONFIGURED -> log.info("01.672.020 Key Vault boot-unlock is not configured (no {} in mh.home, {} is blank), companyUniqueId={} stays locked",
                VaultBootUnlockFileUtils.BOOT_UNLOCK_FILE, PROPERTY_NAME, companyUniqueId);
            case KEK_MISSING -> log.warn("01.672.030 the encrypted passphrase is configured but the environment variable {} with the KEK is not set, companyUniqueId={} stays locked",
                kekEnv, companyUniqueId);
            case NO_VAULT -> log.warn("01.672.040 companyUniqueId={} has no persisted Key Vault, boot-unlock never creates one", companyUniqueId);
            case DECRYPT_FAILED -> log.error("01.672.050 the encrypted passphrase can't be decrypted with the KEK from {} - wrong KEK, a value produced for another company, or a damaged value. companyUniqueId={} stays locked",
                kekEnv, companyUniqueId);
            case UNLOCK_FAILED -> log.error("01.672.060 the recovered passphrase didn't open the Key Vault of companyUniqueId={} - was the passphrase changed?",
                companyUniqueId);
            case UNLOCKED -> log.info("01.672.070 Key Vault of companyUniqueId={} unlocked at Dispatcher start", companyUniqueId);
            case READY -> {
                // not a terminal status - resolved above
            }
        }
        return status;
    }

    /**
     * Produce the {@link #PROPERTY_NAME} value for the management company. The passphrase is verified against
     * the open Vault and encrypted with the KEK of THIS Dispatcher process, so the value is decryptable by the
     * same KEK at the next start - and the KEK never leaves the process.
     *
     * <p>The value is written to {@code ${mh.home}/vault-boot-unlock.txt} and read from there at the next start;
     * the result carries the file's path, not the value.
     */
    public VaultData.BootUnlockValue bootUnlockValue(long companyUniqueId, @Nullable String passphrase, Function<String, @Nullable String> env) {
        if (companyUniqueId != Consts.MANAGEMENT_COMPANY_ID) {
            return new VaultData.BootUnlockValue("01.672.080 Auto-unlock at Dispatcher start is available for the management company only");
        }
        if (!vaultService.isOpened(companyUniqueId)) {
            return new VaultData.BootUnlockValue("01.672.090 Vault is locked");
        }
        if (passphrase == null || !vaultService.verifyPassphrase(companyUniqueId, passphrase)) {
            return new VaultData.BootUnlockValue("01.672.100 Passphrase verification failed");
        }
        final Globals.BootUnlock cfg = globals.dispatcher.vault.bootUnlock;
        final String kek = env.apply(cfg.kekEnv);
        if (kek == null || kek.isBlank()) {
            return new VaultData.BootUnlockValue("01.672.110 Environment variable " + cfg.kekEnv + " with the KEK isn't set for this Dispatcher process");
        }
        final String encrypted;
        try {
            encrypted = VaultBootUnlockUtils.encryptPassphrase(kek, passphrase, companyUniqueId);
        }
        catch (Exception e) {
            log.error("01.672.120 Failed to encrypt the passphrase with the KEK from {}: {}", cfg.kekEnv, e.getMessage());
            return new VaultData.BootUnlockValue("01.672.130 Failed to encrypt the passphrase with the KEK from " + cfg.kekEnv + ", it must be Base64 of 32 bytes");
        }
        try {
            final Path file = VaultBootUnlockFileUtils.writeEncryptedPassphrase(globals.getHome(), encrypted);
            log.info("01.672.140 Key Vault boot-unlock enabled for companyUniqueId={}, written to {}", companyUniqueId, file);
            return new VaultData.BootUnlockValue(file.toAbsolutePath().toString(), cfg.kekEnv);
        }
        catch (Exception e) {
            log.error("01.672.150 Failed to write {} to mh.home: {}", VaultBootUnlockFileUtils.BOOT_UNLOCK_FILE, e.getMessage());
            return new VaultData.BootUnlockValue("01.672.160 Failed to write " + VaultBootUnlockFileUtils.BOOT_UNLOCK_FILE + " to mh.home: " + e.getMessage());
        }
    }
}
