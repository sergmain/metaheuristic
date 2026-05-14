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

import ai.metaheuristic.commons.security.SealedSecret;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT;

/**
 * Pure unit tests for {@link SealedSecretCache}. No Spring context.
 *
 * @author Sergio Lissner
 */
@Execution(CONCURRENT)
public class SealedSecretCacheTest {

    private static SealedSecret dummySealed() {
        return new SealedSecret(
                SealedSecret.VERSION_1,
                new byte[] { 1, 2, 3 },
                new byte[] { 4, 5, 6 },
                new byte[] { 7, 8, 9 });
    }

    @Test
    public void test_get_returnsNull_onMiss() {
        SealedSecretCache cache = new SealedSecretCache();
        assertNull(cache.get(7L, "openai_api_key"));
    }

    @Test
    public void test_get_returnsSealed_onHit() {
        SealedSecretCache cache = new SealedSecretCache();
        SealedSecret sealed = dummySealed();
        long notAfter = System.currentTimeMillis() + 60_000L;

        cache.put(42L, "openai_api_key", sealed, "fp1", notAfter);

        SealedSecret got = cache.get(42L, "openai_api_key");
        assertSame(sealed, got);
        assertEquals(1, cache.size());
    }

    @Test
    public void test_get_evictsAndReturnsNull_onTtlExpiry() {
        SealedSecretCache cache = new SealedSecretCache();
        long expired = System.currentTimeMillis() - 1L;
        cache.put(42L, "openai_api_key", dummySealed(), "fp1", expired);

        assertNull(cache.get(42L, "openai_api_key"));
        assertEquals(0, cache.size(), "TTL miss must lazy-evict");
    }

    @Test
    public void test_invalidate_removesEntry() {
        SealedSecretCache cache = new SealedSecretCache();
        long notAfter = System.currentTimeMillis() + 60_000L;
        cache.put(42L, "openai_api_key", dummySealed(), "fp1", notAfter);
        assertNotNull(cache.get(42L, "openai_api_key"));

        cache.invalidate(42L, "openai_api_key");

        assertNull(cache.get(42L, "openai_api_key"));
        assertEquals(0, cache.size());
    }

    @Test
    public void test_invalidate_isNoop_whenKeyAbsent() {
        SealedSecretCache cache = new SealedSecretCache();
        cache.invalidate(42L, "missing");
        assertEquals(0, cache.size());
    }

    @Test
    public void test_keyByCompanyId_isolatesDifferentCompanies() {
        SealedSecretCache cache = new SealedSecretCache();
        SealedSecret sealedA = dummySealed();
        SealedSecret sealedB = new SealedSecret(SealedSecret.VERSION_1, new byte[]{10}, new byte[]{11}, new byte[]{12});
        long notAfter = System.currentTimeMillis() + 60_000L;

        cache.put(7L, "k", sealedA, "fpA", notAfter);
        cache.put(8L, "k", sealedB, "fpB", notAfter);

        assertSame(sealedA, cache.get(7L, "k"));
        assertSame(sealedB, cache.get(8L, "k"));
        assertEquals(2, cache.size());
    }

    @Test
    public void test_put_replacesEntryForSameKey() {
        SealedSecretCache cache = new SealedSecretCache();
        SealedSecret first = dummySealed();
        SealedSecret second = new SealedSecret(SealedSecret.VERSION_1, new byte[]{99}, new byte[]{98}, new byte[]{97});
        long notAfter = System.currentTimeMillis() + 60_000L;

        cache.put(7L, "k", first, "fp1", notAfter);
        cache.put(7L, "k", second, "fp2", notAfter);

        assertSame(second, cache.get(7L, "k"));
        assertEquals(1, cache.size());
    }

    @Test
    public void test_clearAll_dropsEverything() {
        SealedSecretCache cache = new SealedSecretCache();
        long notAfter = System.currentTimeMillis() + 60_000L;
        cache.put(7L, "a", dummySealed(), "fp", notAfter);
        cache.put(7L, "b", dummySealed(), "fp", notAfter);
        cache.put(8L, "c", dummySealed(), "fp", notAfter);

        cache.clearAll();

        assertEquals(0, cache.size());
        assertNull(cache.get(7L, "a"));
    }
}
