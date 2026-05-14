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

import java.io.FileDescriptor;
import java.lang.reflect.Field;
import java.net.Socket;
import java.net.SocketImpl;

/**
 * Resolves the PID of the process on the other end of a connected loopback
 * {@link Socket}, by delegating to a platform-specific FFM downcall.
 *
 * <p>Returns {@code -1} when verification is not available (unsupported
 * platform, FFM/reflection blocked by JVM flags, or a kernel error).
 * Callers MUST treat {@code -1} as a verification failure and fail closed —
 * the secret-channel handoff rejects {@code -1} and throws
 * {@link SecurityException}. Combined with the per-launch check-code in
 * {@code TaskFileParamsYaml.Task.checkCode}, this gives belt-and-suspenders:
 * the attacker must both win the loopback race AND know the check-code that
 * was written only to a task-scoped params file.
 *
 * <p>Error-code prefix {@code 666.}.
 *
 * @author Sergio Lissner
 */
@Slf4j
public final class PeerPidVerifier {

    private PeerPidVerifier() {}

    public enum Platform { LINUX, MACOS, WINDOWS, OTHER }

    public static Platform currentPlatform() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("linux"))   return Platform.LINUX;
        if (os.contains("mac"))     return Platform.MACOS;
        if (os.contains("windows")) return Platform.WINDOWS;
        return Platform.OTHER;
    }

    /**
     * Returns the PID of the connected peer, or {@code -1} on any failure.
     *
     * @param socket a connected (post-{@code accept()}) loopback socket
     */
    public static long peerPid(Socket socket) {
        try {
            int fd = extractFd(socket);
            return switch (currentPlatform()) {
                case LINUX   -> PeerPidVerifierLinux.peerPid(fd);
                case MACOS   -> PeerPidVerifierMacOs.peerPid(fd);
                case WINDOWS -> PeerPidVerifierWindows.peerPid(fd);
                case OTHER   -> {
                    log.warn("666.020 peer PID verification unavailable on this platform");
                    yield -1L;
                }
            };
        } catch (Throwable t) {
            log.warn("666.030 peer PID verification failed: {}", t.toString());
            return -1L;
        }
    }

    /**
     * Extracts the raw integer file descriptor from a {@link Socket} via
     * reflection on {@code SocketImpl.fd}. Requires
     * {@code --add-opens=java.base/java.net=ALL-UNNAMED} and
     * {@code --add-opens=java.base/java.io=ALL-UNNAMED} on the Processor JVM.
     * If a future JDK removes these private fields, this helper throws and the
     * outer {@link #peerPid(Socket)} returns {@code -1}.
     *
     * <p>Pulled out as a package-private static so the platform impls can be
     * unit-tested by passing a real, locally-bound socket's FD.
     */
    static int extractFd(Socket socket) throws Exception {
        Field implField = Socket.class.getDeclaredField("impl");
        implField.setAccessible(true);
        SocketImpl impl = (SocketImpl) implField.get(socket);

        Field fdField = SocketImpl.class.getDeclaredField("fd");
        fdField.setAccessible(true);
        FileDescriptor fd = (FileDescriptor) fdField.get(impl);

        Field rawFdField = FileDescriptor.class.getDeclaredField("fd");
        rawFdField.setAccessible(true);
        return (int) rawFdField.get(fd);
    }
}
