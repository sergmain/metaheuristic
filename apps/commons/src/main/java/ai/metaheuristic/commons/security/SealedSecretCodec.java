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

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Binary serializer for {@link SealedSecret}. The YAML layer Base64-encodes the
 * resulting byte[] when persisting.
 *
 * <p>Wire format:
 * <pre>
 *   offset  size      field
 *   0       1         version (must be 0x01)
 *   1       2         big-endian uint16: wrappedAesKey length (W)
 *   3       W         wrappedAesKey
 *   3+W     12        gcmIv
 *   15+W    4         big-endian uint32: ciphertextWithTag length (M)
 *   19+W    M         ciphertextWithTag
 * </pre>
 *
 * @author Sergio Lissner
 */
public final class SealedSecretCodec {

    private static final int GCM_IV_LEN = 12;
    private static final int U16_MAX = 0xFFFF;

    private SealedSecretCodec() {
        // static-only
    }

    public static byte[] toBytes(SealedSecret s) {
        byte[] wrapped = s.wrappedAesKey();
        byte[] iv = s.gcmIv();
        byte[] ct = s.ciphertextWithTag();

        if (wrapped.length > U16_MAX) {
            throw new IllegalArgumentException("wrappedAesKey too large for uint16 length prefix: " + wrapped.length);
        }
        if (iv.length != GCM_IV_LEN) {
            throw new IllegalArgumentException("Expected GCM IV length=" + GCM_IV_LEN + ", got " + iv.length);
        }

        int total = 1 + 2 + wrapped.length + GCM_IV_LEN + 4 + ct.length;
        ByteBuffer out = ByteBuffer.allocate(total).order(ByteOrder.BIG_ENDIAN);
        out.put(s.version());
        out.putShort((short) (wrapped.length & 0xFFFF));
        out.put(wrapped);
        out.put(iv);
        out.putInt(ct.length);
        out.put(ct);
        return out.array();
    }

    public static SealedSecret fromBytes(byte[] raw) {
        if (raw.length < 1) {
            throw new IllegalArgumentException("SealedSecret payload too short");
        }
        ByteBuffer in = ByteBuffer.wrap(raw).order(ByteOrder.BIG_ENDIAN);
        try {
            byte version = in.get();
            if (version != SealedSecret.VERSION_1) {
                throw new IllegalArgumentException("Unsupported SealedSecret version: " + version);
            }
            int wLen = in.getShort() & 0xFFFF;
            byte[] wrapped = new byte[wLen];
            in.get(wrapped);

            byte[] iv = new byte[GCM_IV_LEN];
            in.get(iv);

            int mLen = in.getInt();
            if (mLen < 0 || mLen > in.remaining()) {
                throw new IllegalArgumentException("Bad ciphertextWithTag length: " + mLen);
            }
            byte[] ct = new byte[mLen];
            in.get(ct);

            return new SealedSecret(version, wrapped, iv, ct);
        } catch (BufferUnderflowException e) {
            throw new IllegalArgumentException("SealedSecret payload truncated", e);
        }
    }
}
