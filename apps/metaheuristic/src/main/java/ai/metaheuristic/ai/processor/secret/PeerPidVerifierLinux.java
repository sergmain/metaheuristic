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
 * Linux peer-PID verifier via {@code getsockopt(SO_PEERCRED)}.
 *
 * <p>The kernel fills {@code struct ucred { uint32_t pid; uint32_t uid; uint32_t gid; }}
 * (verified against the runtime via a probe: {@code sizeof == 12}, pid at
 * offset 0). The pid is captured at the moment of {@code connect()}, so it's
 * stable even if the peer forks or exits afterward.
 *
 * <p>Constants verified at runtime: {@code SOL_SOCKET = 1}, {@code SO_PEERCRED = 17}.
 *
 * @author Sergio Lissner
 */
@Slf4j
public final class PeerPidVerifierLinux {

    private PeerPidVerifierLinux() {}

    private static final int SOL_SOCKET  = 1;
    private static final int SO_PEERCRED = 17;
    private static final int UCRED_SIZE  = 12;

    private static final Linker        LINKER     = Linker.nativeLinker();
    private static final SymbolLookup  STDLIB     = LINKER.defaultLookup();
    private static final MethodHandle  GETSOCKOPT = LINKER.downcallHandle(
            STDLIB.find("getsockopt").orElseThrow(
                    () -> new IllegalStateException("666.041 libc 'getsockopt' symbol not found")),
            FunctionDescriptor.of(
                    ValueLayout.JAVA_INT,    // return: 0 on success, -1 on failure
                    ValueLayout.JAVA_INT,    // int sockfd
                    ValueLayout.JAVA_INT,    // int level
                    ValueLayout.JAVA_INT,    // int optname
                    ValueLayout.ADDRESS,     // void *optval
                    ValueLayout.ADDRESS));   // socklen_t *optlen

    /**
     * @param sockfd raw file descriptor of a connected loopback socket
     * @return the peer's PID on success; {@code -1} on any failure
     */
    public static long peerPid(int sockfd) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment ucred = arena.allocate(UCRED_SIZE);
            MemorySegment optlen = arena.allocate(ValueLayout.JAVA_INT);
            optlen.set(ValueLayout.JAVA_INT, 0, UCRED_SIZE);

            int rc = (int) GETSOCKOPT.invoke(sockfd, SOL_SOCKET, SO_PEERCRED, ucred, optlen);
            if (rc != 0) {
                log.warn("666.042 getsockopt(SO_PEERCRED) returned {}, fd={}", rc, sockfd);
                return -1L;
            }
            int written = optlen.get(ValueLayout.JAVA_INT, 0);
            if (written < UCRED_SIZE) {
                log.warn("666.043 getsockopt(SO_PEERCRED) wrote only {} bytes (expected {})", written, UCRED_SIZE);
                return -1L;
            }
            // pid is at offset 0; uint32 fits in a long
            return Integer.toUnsignedLong(ucred.get(ValueLayout.JAVA_INT, 0));
        } catch (Throwable t) {
            log.warn("666.044 PeerPidVerifierLinux.peerPid failed: {}", t.toString());
            return -1L;
        }
    }
}
