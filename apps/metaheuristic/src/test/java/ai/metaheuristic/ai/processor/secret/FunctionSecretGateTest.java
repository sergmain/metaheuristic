/*
 * Metaheuristic, Copyright (C) 2017-2026, Innovation platforms, LLC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3 of the License.
 */

package ai.metaheuristic.ai.processor.secret;

import ai.metaheuristic.commons.security.SealedSecret;
import ai.metaheuristic.commons.yaml.task.TaskParamsYaml;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT;

/**
 * Pure-unit tests for {@link FunctionSecretGate#decide}. No Spring, no mocks.
 * Each test builds its own state, so {@code @Execution(CONCURRENT)} is safe.
 *
 * @author Sergio Lissner
 */
@Execution(CONCURRENT)
public class FunctionSecretGateTest {

    private static SealedSecret dummySealed() {
        return new SealedSecret(SealedSecret.VERSION_1, new byte[]{1}, new byte[]{2}, new byte[]{3});
    }

    private static Function<String, SealedSecret> empty() {
        return k -> null;
    }

    private static Function<String, SealedSecret> oneEntry(String key, SealedSecret value) {
        Map<String, SealedSecret> m = new HashMap<>();
        m.put(key, value);
        return m::get;
    }

    @Test
    public void test_decide_returnsNoSecretNeeded_whenApiIsNull() {
        FunctionSecretGate.Outcome out =
                FunctionSecretGate.decide(null, 7L, empty());
        assertEquals(FunctionSecretGate.Decision.NO_SECRET_NEEDED, out.decision());
        assertNull(out.keyCode());
        assertNull(out.sealed());
    }

    @Test
    public void test_decide_returnsNoSecretNeeded_whenKeyCodeIsNull() {
        TaskParamsYaml.Api api = new TaskParamsYaml.Api(null);
        FunctionSecretGate.Outcome out = FunctionSecretGate.decide(api, 7L, empty());
        assertEquals(FunctionSecretGate.Decision.NO_SECRET_NEEDED, out.decision());
    }

    @Test
    public void test_decide_returnsNoSecretNeeded_whenKeyCodeIsBlank() {
        TaskParamsYaml.Api api = new TaskParamsYaml.Api("   ");
        FunctionSecretGate.Outcome out = FunctionSecretGate.decide(api, 7L, empty());
        assertEquals(FunctionSecretGate.Decision.NO_SECRET_NEEDED, out.decision());
    }

    @Test
    public void test_decide_returnsNoSecretNeeded_whenCompanyIdIsZero() {
        TaskParamsYaml.Api api = new TaskParamsYaml.Api("openai_api_key");
        FunctionSecretGate.Outcome out = FunctionSecretGate.decide(api, 0L, empty());
        assertEquals(FunctionSecretGate.Decision.NO_SECRET_NEEDED, out.decision());
    }

    @Test
    public void test_decide_returnsCacheMissFetch_whenLookupReturnsNull() {
        TaskParamsYaml.Api api = new TaskParamsYaml.Api("openai_api_key");
        FunctionSecretGate.Outcome out = FunctionSecretGate.decide(api, 7L, empty());
        assertEquals(FunctionSecretGate.Decision.CACHE_MISS_FETCH, out.decision());
        assertEquals("openai_api_key", out.keyCode());
        assertNull(out.sealed());
    }

    @Test
    public void test_decide_returnsCacheHitDecrypt_whenLookupReturnsSealed() {
        TaskParamsYaml.Api api = new TaskParamsYaml.Api("openai_api_key");
        SealedSecret sealed = dummySealed();
        FunctionSecretGate.Outcome out =
                FunctionSecretGate.decide(api, 7L, oneEntry("openai_api_key", sealed));
        assertEquals(FunctionSecretGate.Decision.CACHE_HIT_DECRYPT, out.decision());
        assertEquals("openai_api_key", out.keyCode());
        assertSame(sealed, out.sealed());
    }

    @Test
    public void test_decide_drivesRealCacheBackedLookup() {
        SealedSecretCache cache = new SealedSecretCache();
        SealedSecret sealed = dummySealed();
        long notAfter = System.currentTimeMillis() + 60_000L;
        cache.put(7L, "openai_api_key", sealed, "fp", notAfter);

        // bind the cache's two-arg get(companyId, keyCode) to companyId=7L
        Function<String, SealedSecret> lookup = k -> cache.get(7L, k);

        TaskParamsYaml.Api api = new TaskParamsYaml.Api("openai_api_key");
        FunctionSecretGate.Outcome out = FunctionSecretGate.decide(api, 7L, lookup);

        assertEquals(FunctionSecretGate.Decision.CACHE_HIT_DECRYPT, out.decision());
        assertSame(sealed, out.sealed());
    }
}
