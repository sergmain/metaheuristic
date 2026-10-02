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
package ai.metaheuristic.apps.gen_keys;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * KEK (key-encryption key) for the Key Vault boot-unlock of the Dispatcher.
 *
 * <p>The Dispatcher reads it from the environment variable named by
 * {@code mh.dispatcher.vault.boot-unlock.kek-env} (default {@code MH_VAULT_KEK}) and accepts only
 * Base64 of exactly {@link #KEK_LEN} bytes - any other length is rejected at start with 01.671.010.
 *
 * @author Sergio Lissner
 * Date: 10/2/2026
 */
public final class KekUtils {

    /** AES-256. Must equal {@code VaultBootUnlockUtils.KEK_LEN} in apps/metaheuristic. */
    public static final int KEK_LEN = 32;

    /** Command-line argument that selects KEK generation instead of the RSA key pair. */
    public static final String KEK_COMMAND = "kek";

    private KekUtils() {
        // utility class
    }

    /** @return Base64 of {@link #KEK_LEN} bytes drawn from {@code random} */
    public static String generateKek(SecureRandom random) {
        final byte[] kek = new byte[KEK_LEN];
        random.nextBytes(kek);
        try {
            return Base64.getEncoder().encodeToString(kek);
        }
        finally {
            Arrays.fill(kek, (byte) 0);
        }
    }

    /** {@code kek} or {@code --kek} anywhere among the arguments. */
    public static boolean isKekCommand(String... args) {
        return Arrays.stream(args).anyMatch(a -> KEK_COMMAND.equals(a) || ("--" + KEK_COMMAND).equals(a));
    }
}
