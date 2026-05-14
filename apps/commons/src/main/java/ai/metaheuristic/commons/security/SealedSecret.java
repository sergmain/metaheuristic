/*
 * Metaheuristic, Copyright (C) 2017-2025, Innovation platforms, LLC
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

package ai.metaheuristic.commons.security;

/**
 * Hybrid-encrypted secret, version 1.
 *
 * <p>Logical fields, kept independent so callers can serialize / hash / inspect
 * them without re-parsing a flat byte[]:
 * <ul>
 *   <li>{@code version}            — 1-byte version tag, currently {@link #VERSION_1}.</li>
 *   <li>{@code wrappedAesKey}      — RSA-OAEP-wrapped AES-256 key (length = RSA modulus size in bytes).</li>
 *   <li>{@code gcmIv}              — 12-byte GCM IV.</li>
 *   <li>{@code ciphertextWithTag}  — GCM ciphertext concatenated with the 16-byte GCM tag (JCE convention).</li>
 * </ul>
 *
 * <p>Wire format is defined by {@link SealedSecretCodec}.
 *
 * @author Sergio Lissner
 */
public record SealedSecret(
    byte version,
    byte[] wrappedAesKey,
    byte[] gcmIv,
    byte[] ciphertextWithTag
) {
    public static final byte VERSION_1 = 0x01;
}
