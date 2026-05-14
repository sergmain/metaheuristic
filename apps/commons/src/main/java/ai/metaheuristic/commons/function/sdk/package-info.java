/*
 * Metaheuristic, Copyright (C) 2017-2026, Innovation platforms, LLC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3 of the License.
 */

/**
 * Reference Java client for the Function Secret Channel handoff protocol.
 *
 * <p>Function authors writing JVM-language Functions should depend on
 * {@code mh-commons} and use
 * {@link ai.metaheuristic.commons.function.sdk.MhSecretClient#openFromArgv}.
 *
 * <p>Protocol spec:
 * {@code java/legal/docs/vault-secret-handoff/VAULT-SECRET-HANDOFF-PROTOCOL.md}.
 *
 * <p>Security expectations on the Function author:
 * <ul>
 *   <li>Use the handle inside try-with-resources so {@link
 *       ai.metaheuristic.commons.function.sdk.MhSecretHandle#close} zeroes
 *       the buffer.</li>
 *   <li>Keep the key bytes' lifetime as short as possible.</li>
 *   <li>Do NOT log, persist to disk, place in env, or pass as cmdline arg
 *       to subprocesses.</li>
 *   <li>Prefer the live {@code byte[]} for HTTP header construction over
 *       {@link ai.metaheuristic.commons.function.sdk.MhSecretHandle#asString};
 *       Java strings cannot be zeroed.</li>
 * </ul>
 */
package ai.metaheuristic.commons.function.sdk;
