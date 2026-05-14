/*
 * Metaheuristic, Copyright (C) 2017-2026, Innovation platforms, LLC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3 of the License.
 */

package ai.metaheuristic.ai.processor.secret;

import lombok.extern.slf4j.Slf4j;

/**
 * Windows peer-PID verifier — no clean equivalent for loopback TCP sockets.
 *
 * <p>{@code WSAIoctl} has options for named pipes and AF_UNIX sockets
 * (Windows 10+), but not for AF_INET loopback. Enumerating
 * {@code GetExtendedTcpTable} and matching local-port → PID is itself racy
 * and slow.
 *
 * <p>Always returns {@code -1}. The secret-channel treats this as a
 * verification failure and fails closed. Operators on Windows must rely on
 * the check-code defense in {@code TaskFileParamsYaml.Task.checkCode} alone,
 * which is acceptable per the plan's threat model ("Windows: residual race").
 *
 * <p>If we ever need to actually allow Windows hosts: add an opt-in
 * configuration flag that downgrades the {@code -1} from fail-closed to
 * warn-and-proceed. Not done here.
 *
 * @author Sergio Lissner
 */
@Slf4j
public final class PeerPidVerifierWindows {

    private PeerPidVerifierWindows() {}

    public static long peerPid(int sockfd) {
        log.warn("666.060 PeerPidVerifierWindows.peerPid(): no equivalent of SO_PEERCRED for loopback TCP; returning -1 (fail closed)");
        return -1L;
    }
}
