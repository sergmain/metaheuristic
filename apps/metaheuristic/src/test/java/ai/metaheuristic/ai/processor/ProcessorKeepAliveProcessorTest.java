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

package ai.metaheuristic.ai.processor;

import ai.metaheuristic.ai.yaml.communication.keep_alive.KeepAliveResponseParamYaml;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

import static ai.metaheuristic.ai.processor.ProcessorKeepAliveProcessor.processVaultInvalidations;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT;

/**
 * Pure-unit tests for {@link ProcessorKeepAliveProcessor#processVaultInvalidations}.
 *
 * <p>Each test builds its own capture list and lambda — no shared state, no
 * framework, no mocks. {@code @Execution(CONCURRENT)} is legitimate here.
 *
 * @author Sergio Lissner
 */
@Execution(CONCURRENT)
public class ProcessorKeepAliveProcessorTest {

    /** Captures (companyId, keyCode) pairs in order. */
    private static class Captor {
        final List<Map.Entry<Long, String>> calls = new ArrayList<>();
        final BiConsumer<Long, String> sink = (c, k) -> calls.add(Map.entry(c, k));
    }

    private static KeepAliveResponseParamYaml.VaultEntryInvalidation inv(long companyId, String keyCode) {
        return new KeepAliveResponseParamYaml.VaultEntryInvalidation(companyId, keyCode, "put", 1L);
    }

    @Test
    public void test_processVaultInvalidations_nullList_isNoop() {
        Captor cap = new Captor();
        processVaultInvalidations(null, cap.sink);
        assertTrue(cap.calls.isEmpty(), "null list must not trigger any invalidation");
    }

    @Test
    public void test_processVaultInvalidations_emptyList_isNoop() {
        Captor cap = new Captor();
        processVaultInvalidations(List.of(), cap.sink);
        assertTrue(cap.calls.isEmpty(), "empty list must not trigger any invalidation");
    }

    @Test
    public void test_processVaultInvalidations_singleEntry_invokesOnce() {
        Captor cap = new Captor();
        processVaultInvalidations(List.of(inv(7L, "openai_api_key")), cap.sink);
        assertEquals(1, cap.calls.size());
        assertEquals(Map.entry(7L, "openai_api_key"), cap.calls.get(0));
    }

    @Test
    public void test_processVaultInvalidations_multipleEntries_invokesEachInOrder() {
        Captor cap = new Captor();
        List<KeepAliveResponseParamYaml.VaultEntryInvalidation> list = List.of(
                inv(7L, "openai_api_key"),
                inv(8L, "anthropic_api_key"),
                inv(7L, "stripe_secret")
        );
        processVaultInvalidations(list, cap.sink);
        assertEquals(3, cap.calls.size());
        assertEquals(Map.entry(7L, "openai_api_key"),    cap.calls.get(0));
        assertEquals(Map.entry(8L, "anthropic_api_key"), cap.calls.get(1));
        assertEquals(Map.entry(7L, "stripe_secret"),     cap.calls.get(2));
    }

    @Test
    public void test_processVaultInvalidations_skipsNullEntries() {
        Captor cap = new Captor();
        List<KeepAliveResponseParamYaml.VaultEntryInvalidation> list = new ArrayList<>();
        list.add(inv(7L, "a"));
        list.add(null);
        list.add(inv(7L, "b"));
        processVaultInvalidations(list, cap.sink);
        assertEquals(2, cap.calls.size());
        assertEquals("a", cap.calls.get(0).getValue());
        assertEquals("b", cap.calls.get(1).getValue());
    }

    @Test
    public void test_processVaultInvalidations_skipsEntriesWithNullKeyCode() {
        Captor cap = new Captor();
        KeepAliveResponseParamYaml.VaultEntryInvalidation bad =
                new KeepAliveResponseParamYaml.VaultEntryInvalidation(7L, null, "put", 1L);
        processVaultInvalidations(List.of(inv(7L, "good"), bad, inv(7L, "also_good")), cap.sink);
        assertEquals(2, cap.calls.size());
        assertEquals("good",      cap.calls.get(0).getValue());
        assertEquals("also_good", cap.calls.get(1).getValue());
    }

    /**
     * End-to-end behaviour check: wire the static helper to a real
     * concurrent map (the shape {@code SealedSecretCache} uses internally) and
     * confirm that the targeted keys are removed and untargeted keys survive.
     */
    @Test
    public void test_processVaultInvalidations_drivesRealMapEviction() {
        ConcurrentHashMap<String, String> store = new ConcurrentHashMap<>();
        store.put("7|openai_api_key",    "sealedA");
        store.put("8|anthropic_api_key", "sealedB");
        store.put("7|stripe_secret",     "sealedC");
        store.put("9|untouched",         "sealedD");

        BiConsumer<Long, String> evict = (companyId, keyCode) ->
                store.remove(companyId + "|" + keyCode);

        processVaultInvalidations(List.of(
                inv(7L, "openai_api_key"),
                inv(7L, "stripe_secret")
        ), evict);

        assertNull(store.get("7|openai_api_key"));
        assertNull(store.get("7|stripe_secret"));
        assertEquals("sealedB", store.get("8|anthropic_api_key"));
        assertEquals("sealedD", store.get("9|untouched"));
    }
}
