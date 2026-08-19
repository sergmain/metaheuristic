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

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Process-wide cache of the dispatcher-wide list of supported locales.
 *
 * <p>Why it exists: the list is read by the language selector on the anonymous index page, i.e.
 * before any authentication, so the read is reachable by anyone who can reach the port. Answering
 * it from a params lookup per hit would make an unauthenticated request do real work; answering it
 * from a slot in memory does not. It changes only when an admin saves a new list, which is what
 * {@link #reset()} is for.
 *
 * <p>The mutable slot is a parameter on the package-private overloads, so the whole rule is
 * exercisable without touching the global one. That is what lets the test run CONCURRENT.
 *
 * <p>Reset ordering matters at the call site: the store must be written FIRST and the cache reset
 * after it. Resetting first leaves a window in which a concurrent reader reloads the value that is
 * about to be replaced and caches it again - and nothing would reset it a second time.
 *
 * @author Sergio Lissner
 * Date: 8/19/2026
 */
public class SupportedLanguagesCache {

    private static final AtomicReference<@Nullable List<String>> LOCALES = new AtomicReference<>(null);

    public static List<String> get(Supplier<List<String>> loader) {
        return get(LOCALES, loader);
    }

    public static void reset() {
        reset(LOCALES);
    }

    /**
     * Double-checked: the hit path is a plain read of the AtomicReference and takes no lock. Only a
     * miss enters the monitor, and the monitor is what keeps a reset from interleaving between the
     * load and the publish - without it, a reset landing mid-load would be overwritten by the stale
     * value the loader had already produced.
     */
    static List<String> get(AtomicReference<@Nullable List<String>> slot, Supplier<List<String>> loader) {
        final List<String> cached = slot.get();
        if (cached!=null) {
            return cached;
        }
        synchronized (slot) {
            final List<String> second = slot.get();
            if (second!=null) {
                return second;
            }
            // an immutable copy: a caller that mutates what it was handed would otherwise corrupt
            // every later hit, and the corruption would outlive the request that caused it
            final List<String> loaded = List.copyOf(loader.get());
            slot.set(loaded);
            return loaded;
        }
    }

    static void reset(AtomicReference<@Nullable List<String>> slot) {
        synchronized (slot) {
            slot.set(null);
        }
    }
}
