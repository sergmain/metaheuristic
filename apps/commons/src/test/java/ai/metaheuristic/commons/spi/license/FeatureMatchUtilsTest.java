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

package ai.metaheuristic.commons.spi.license;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT;

/**
 * The Category:VALUE feature model and exact, grant-only matching. The vocabulary used here is
 * deliberately made-up - the matcher must behave identically for categories it has never seen,
 * which is the whole point of keeping it string-only.
 *
 * Note what is NOT here: no Database or Storage category. Those axes are explicit claim fields
 * checked against the running deployment, not opaque features gated at a call site.
 *
 * @author Serge
 */
@Execution(CONCURRENT)
public class FeatureMatchUtilsTest {

    private static final Set<String> GRANTED = Set.of("Cat.ALPHA", "Cat.BETA", "Other.ALPHA");

    @Test
    public void test_exactMatch() {
        assertTrue(FeatureMatchUtils.matches(GRANTED, new Feature("Cat", "ALPHA")));
        assertTrue(FeatureMatchUtils.matches(GRANTED, new Feature("Other", "ALPHA")));
    }

    @Test
    public void test_notGranted_sameCategory() {
        assertFalse(FeatureMatchUtils.matches(GRANTED, new Feature("Cat", "GAMMA")));
        assertFalse(FeatureMatchUtils.matches(GRANTED, new Feature("Other", "BETA")));
    }

    @Test
    public void test_notGranted_unknownCategory() {
        assertFalse(FeatureMatchUtils.matches(GRANTED, new Feature("Absent", "ALPHA")));
        assertFalse(FeatureMatchUtils.matches(GRANTED, new Feature("NeverSeen", "BETA")));
    }

    @Test
    public void test_valueDoesNotLeakAcrossCategories() {
        // 'BETA' is granted under 'Cat'; the same value in another category must not match.
        assertFalse(FeatureMatchUtils.matches(GRANTED, new Feature("Other", "BETA")));
    }

    @Test
    public void test_noWildcard() {
        // 'ANY' is an ordinary value with no special meaning - grants are always enumerated.
        final Set<String> granted = Set.of("Cat.ANY");
        assertFalse(FeatureMatchUtils.matches(granted, new Feature("Cat", "ALPHA")));
        assertFalse(FeatureMatchUtils.matches(granted, new Feature("Cat", "BETA")));
        assertTrue(FeatureMatchUtils.matches(granted, new Feature("Cat", "ANY")));
    }

    @Test
    public void test_emptyGrantSet() {
        assertFalse(FeatureMatchUtils.matches(Set.of(), new Feature("Cat", "ALPHA")));
    }

    @Test
    public void test_key_wireForm() {
        assertEquals("Cat.ALPHA", new Feature("Cat", "ALPHA").key());
    }

    @Test
    public void test_blankComponentsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new Feature("", "ALPHA"));
        assertThrows(IllegalArgumentException.class, () -> new Feature("Cat", " "));
    }

    @Test
    public void test_separatorInComponentsRejected() {
        // otherwise 'A.B' + 'C' and 'A' + 'B.C' would collide on the same wire form.
        assertThrows(IllegalArgumentException.class, () -> new Feature("C.at", "ALPHA"));
        assertThrows(IllegalArgumentException.class, () -> new Feature("Cat", "AL.PHA"));
    }
}
