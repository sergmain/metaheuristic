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
 */

package ai.metaheuristic.ai.processor.secret;

import ai.metaheuristic.commons.security.SealedSecret;
import ai.metaheuristic.commons.yaml.task.TaskParamsYaml;
import org.jspecify.annotations.Nullable;

import java.util.function.Function;

/**
 * Decision helper for Task launch-time sealed-secret handling. Pure static
 * function — dependencies passed as lambdas. Testable without Spring or any
 * mock framework.
 *
 * <p>Three decisions:
 * <ul>
 *   <li>{@code NO_SECRET_NEEDED} — Function declares no API key (or
 *       companyId==0L sentinel). Launch as-is.</li>
 *   <li>{@code CACHE_HIT_DECRYPT} — sealed bytes are in cache; caller
 *       decrypts and proceeds with the launch.</li>
 *   <li>{@code CACHE_MISS_FETCH} — caller must enqueue a fetch task and
 *       skip this launch cycle. The next launch attempt will find a
 *       populated cache.</li>
 * </ul>
 *
 * @author Sergio Lissner
 */
public final class FunctionSecretGate {

    private FunctionSecretGate() {}

    public enum Decision { NO_SECRET_NEEDED, CACHE_HIT_DECRYPT, CACHE_MISS_FETCH }

    public record Outcome(Decision decision, @Nullable String keyCode, @Nullable SealedSecret sealed) {}

    /**
     * @param api          the FunctionConfig.api descriptor — null when the
     *                     Function declares no API key
     * @param companyId    the task's companyId; {@code 0L} is the
     *                     greenfield-exception sentinel meaning "no company"
     * @param cacheLookup  given a keyCode, returns the cached SealedSecret or
     *                     null on miss. {@link SealedSecretCache#get} bound
     *                     to a fixed companyId is the canonical impl.
     */
    public static Outcome decide(
            TaskParamsYaml.@Nullable Api api,
            long companyId,
            Function<String, @Nullable SealedSecret> cacheLookup) {

        if (api == null || api.keyCode == null || api.keyCode.isBlank() || companyId == 0L) {
            return new Outcome(Decision.NO_SECRET_NEEDED, null, null);
        }
        SealedSecret sealed = cacheLookup.apply(api.keyCode);
        if (sealed == null) {
            return new Outcome(Decision.CACHE_MISS_FETCH, api.keyCode, null);
        }
        return new Outcome(Decision.CACHE_HIT_DECRYPT, api.keyCode, sealed);
    }
}
