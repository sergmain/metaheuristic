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

package ai.metaheuristic.commons.function.sdk;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * AutoCloseable wrapper around the decrypted API key bytes. Zeroes the
 * internal buffer on {@link #close}. Use inside try-with-resources.
 *
 * <p>{@link #bytes} returns the live internal buffer — do not retain a
 * reference outside the handle's lifetime; the buffer is zeroed on close.
 * {@link #asString} produces an immutable {@code String} (Java strings
 * can't be zeroed) — keep its lifetime short, never log it.
 *
 * @author Sergio Lissner
 */
public final class MhSecretHandle implements AutoCloseable {

    private byte @org.jspecify.annotations.Nullable [] bytes;

    public MhSecretHandle(byte[] bytes) {
        this.bytes = bytes;
    }

    /**
     * Returns the live internal byte buffer. Throws {@link IllegalStateException}
     * after {@link #close}.
     */
    public byte[] bytes() {
        if (bytes == null) {
            throw new IllegalStateException("0667.030 MhSecretHandle already closed");
        }
        return bytes;
    }

    /** Convenience: decode the bytes as UTF-8 into a (non-zeroable) String. */
    public String asString() {
        return new String(bytes(), StandardCharsets.UTF_8);
    }

    public int length() {
        return bytes == null ? 0 : bytes.length;
    }

    @Override
    public void close() {
        if (bytes != null) {
            Arrays.fill(bytes, (byte) 0);
            bytes = null;
        }
    }
}
