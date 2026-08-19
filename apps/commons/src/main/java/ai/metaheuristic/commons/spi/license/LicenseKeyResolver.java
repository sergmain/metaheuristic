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

package ai.metaheuristic.commons.spi.license;

import org.jspecify.annotations.Nullable;

import java.security.interfaces.ECPublicKey;
import java.util.function.Function;

/**
 * kid -> public key, plus the one thing a bare {@code Function} cannot say: whether this
 * installation holds ANY key material at all.
 *
 * <p>❗ Why this type exists. The lookup used to be a plain
 * {@code Function<String, @Nullable ECPublicKey>}, and a null return had to mean two different
 * things at once — "the kid you asked for is not the one I answer to" and "I was never given a key,
 * so I answer to nothing". {@link LicenseTokenCodec} could only report one state for both, and it
 * reported UNKNOWN_KID: an installation with {@code mh.key-store.license.public-key} unset refused
 * every correctly-signed licence by naming its kid, which is the one part of the token that was
 * beyond reproach. Diagnosing it meant reading the resolver's source.
 *
 * <p>The flag is carried rather than derived because only the code that BUILDS the resolver knows
 * the answer; by the time the codec holds a function, the distinction has already been erased.
 *
 * <p>Still functional in shape: the lookup is a lambda supplied by the caller, so a test resolver
 * is one line and no key material has to exist to exercise the codec.
 *
 * @param configured whether any key material was supplied. {@code false} means no kid can ever
 *                   resolve here, and the codec reports NO_VERIFICATION_KEY rather than UNKNOWN_KID.
 * @param keyByKid   the lookup itself; may return null for a kid it does not answer to.
 *
 * @author Serge
 */
public record LicenseKeyResolver(boolean configured, Function<String, @Nullable ECPublicKey> keyByKid) {

    /** No key material at all: nothing verifies, and every licence is refused NO_VERIFICATION_KEY. */
    public static LicenseKeyResolver none() {
        return new LicenseKeyResolver(false, _ -> null);
    }

    /** A resolver built over real key material. A kid it does not answer to is UNKNOWN_KID. */
    public static LicenseKeyResolver of(Function<String, @Nullable ECPublicKey> keyByKid) {
        return new LicenseKeyResolver(true, keyByKid);
    }

    @Nullable
    public ECPublicKey keyFor(String kid) {
        return keyByKid.apply(kid);
    }
}
