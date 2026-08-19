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

package ai.metaheuristic.ai.dispatcher.settings;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT;

/**
 * The cache rule on its own slot, never the global one - every @Test allocates its own
 * AtomicReference, which is what makes the class safe to run CONCURRENT despite the production
 * object being process-wide state.
 *
 * @author Sergio Lissner
 * Date: 8/19/2026
 */
@Execution(CONCURRENT)
public class SupportedLanguagesCacheTest {

    /** A loader that records how many times it was actually asked to produce a value. */
    private record CountingLoader(AtomicInteger calls, List<String> value) {
        List<String> load() {
            calls.incrementAndGet();
            return value;
        }
    }

    private static AtomicReference<@Nullable List<String>> emptySlot() {
        return new AtomicReference<>(null);
    }

    /**
     * The whole point of the cache: an empty slot must consult the loader once, and every later hit
     * must be answered without touching it. If this regresses, each anonymous page load costs a
     * dispatcher-params lookup, which is exactly what the anon endpoint must not do.
     */
    @Test
    public void test_get_loadsOnceThenServesFromSlot() {
        final AtomicReference<@Nullable List<String>> slot = emptySlot();
        final CountingLoader loader = new CountingLoader(new AtomicInteger(0), List.of("en", "ru"));

        assertEquals(List.of("en", "ru"), SupportedLanguagesCache.get(slot, loader::load));
        assertEquals(1, loader.calls().get());

        assertEquals(List.of("en", "ru"), SupportedLanguagesCache.get(slot, loader::load));
        assertEquals(List.of("en", "ru"), SupportedLanguagesCache.get(slot, loader::load));
        assertEquals(1, loader.calls().get(), "the loader was consulted again on a cache hit");
    }

    /**
     * An admin saving a new list must actually become visible. Without the reset the selector would
     * keep offering the outgoing list for the lifetime of the process.
     */
    @Test
    public void test_reset_forcesTheNextGetToReload() {
        final AtomicReference<@Nullable List<String>> slot = emptySlot();
        final AtomicInteger calls = new AtomicInteger(0);
        final AtomicReference<List<String>> stored = new AtomicReference<>(List.of("en"));

        assertEquals(List.of("en"), SupportedLanguagesCache.get(slot, () -> { calls.incrementAndGet(); return stored.get(); }));
        assertEquals(1, calls.get());

        // an admin saves a new list: store first, then reset - the order used by SettingsService
        stored.set(List.of("en", "de"));
        SupportedLanguagesCache.reset(slot);

        assertEquals(List.of("en", "de"), SupportedLanguagesCache.get(slot, () -> { calls.incrementAndGet(); return stored.get(); }));
        assertEquals(2, calls.get());
    }

    /**
     * A reset on an already-empty slot is a no-op rather than a failure: saveLanguages calls it
     * unconditionally and has no way to know whether anything was ever cached.
     */
    @Test
    public void test_reset_onEmptySlotIsHarmless() {
        final AtomicReference<@Nullable List<String>> slot = emptySlot();
        SupportedLanguagesCache.reset(slot);
        assertNull(slot.get());

        final CountingLoader loader = new CountingLoader(new AtomicInteger(0), List.of("en"));
        assertEquals(List.of("en"), SupportedLanguagesCache.get(slot, loader::load));
        assertEquals(1, loader.calls().get());
    }

    /**
     * What is handed out is immutable. The cached list is shared by every subsequent caller, so a
     * caller that could mutate it would corrupt the answer for requests it has nothing to do with -
     * and the corruption would outlive the request that caused it.
     */
    @Test
    public void test_get_handsOutAnImmutableList() {
        final AtomicReference<@Nullable List<String>> slot = emptySlot();
        final List<String> cached = SupportedLanguagesCache.get(slot, () -> List.of("en", "ru"));

        assertThrows(UnsupportedOperationException.class, () -> cached.add("de"));
        assertThrows(UnsupportedOperationException.class, () -> cached.clear());
        assertEquals(List.of("en", "ru"), SupportedLanguagesCache.get(slot, () -> List.of("should", "not", "be", "used")));
    }

    /**
     * The copy is taken at publish time. SettingsService builds its list with an ArrayList, so a
     * caching implementation that stored the loader's own instance would let a later mutation of
     * that instance rewrite what everyone else sees.
     */
    @Test
    public void test_get_isNotAliasedToTheLoadersOwnList() {
        final AtomicReference<@Nullable List<String>> slot = emptySlot();
        final List<String> mutableFromLoader = new ArrayList<>(List.of("en", "ru"));

        final List<String> cached = SupportedLanguagesCache.get(slot, () -> mutableFromLoader);
        mutableFromLoader.add("de");

        assertEquals(List.of("en", "ru"), cached);
        assertEquals(List.of("en", "ru"), SupportedLanguagesCache.get(slot, () -> List.of("should", "not", "be", "used")));
    }

    /**
     * An empty result is a legitimate value and must be cached as one. Treating "empty" as "not yet
     * loaded" would make every request reload, which is the failure the cache exists to prevent.
     */
    @Test
    public void test_get_cachesAnEmptyResultRatherThanRetrying() {
        final AtomicReference<@Nullable List<String>> slot = emptySlot();
        final CountingLoader loader = new CountingLoader(new AtomicInteger(0), List.of());

        assertEquals(List.of(), SupportedLanguagesCache.get(slot, loader::load));
        assertEquals(List.of(), SupportedLanguagesCache.get(slot, loader::load));
        assertEquals(1, loader.calls().get(), "an empty list was treated as a cache miss");
    }
}
