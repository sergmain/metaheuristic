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

package ai.metaheuristic.ai.processor.secret;

import lombok.extern.slf4j.Slf4j;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

/**
 * macOS peer-PID verifier via {@code getsockopt(LOCAL_PEERPID)}.
 *
 * <p>{@code SOL_LOCAL = 0}, {@code LOCAL_PEERPID = 2} on Darwin XNU. The
 * result is a single {@code pid_t} (4 bytes signed int).
 *
 * <p>Code-reviewed but NOT runtime-tested from the dev/CI host (Linux).
 * macOS deployments must include a startup self-test that exercises this
 * path against a known-PID child process before relying on it in production.
 *
 * @author Sergio Lissner
 */
@Slf4j
public final class PeerPidVerifierMacOs {

    private PeerPidVerifierMacOs() {}

    private static final int SOL_LOCAL     = 0;
    private static final int LOCAL_PEERPID = 2;
    private static final int PID_T_SIZE    = 4;

    private static final Linker       LINKER     = Linker.nativeLinker();
    private static final SymbolLookup STDLIB     = LINKER.defaultLookup();
    private static final MethodHandle GETSOCKOPT = LINKER.downcallHandle(
            STDLIB.find("getsockopt").orElseThrow(
                    () -> new IllegalStateException("666.051 libc 'getsockopt' symbol not found")),
            FunctionDescriptor.of(
                    ValueLayout.JAVA_INT,    // return
                    ValueLayout.JAVA_INT,    // sockfd
                    ValueLayout.JAVA_INT,    // level
                    ValueLayout.JAVA_INT,    // optname
                    ValueLayout.ADDRESS,     // optval
                    ValueLayout.ADDRESS));   // optlen

    public static long peerPid(int sockfd) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment pidBuf = arena.allocate(PID_T_SIZE);
            MemorySegment optlen = arena.allocate(ValueLayout.JAVA_INT);
            optlen.set(ValueLayout.JAVA_INT, 0, PID_T_SIZE);

            int rc = (int) GETSOCKOPT.invoke(sockfd, SOL_LOCAL, LOCAL_PEERPID, pidBuf, optlen);
            if (rc != 0) {
                log.warn("666.052 getsockopt(LOCAL_PEERPID) returned {}, fd={}", rc, sockfd);
                return -1L;
            }
            int written = optlen.get(ValueLayout.JAVA_INT, 0);
            if (written < PID_T_SIZE) {
                log.warn("666.053 getsockopt(LOCAL_PEERPID) wrote only {} bytes (expected {})", written, PID_T_SIZE);
                return -1L;
            }
            return Integer.toUnsignedLong(pidBuf.get(ValueLayout.JAVA_INT, 0));
        } catch (Throwable t) {
            log.warn("666.054 PeerPidVerifierMacOs.peerPid failed: {}", t.toString());
            return -1L;
        }
    }
}
