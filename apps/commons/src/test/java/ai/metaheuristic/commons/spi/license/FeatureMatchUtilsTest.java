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
 * The opaque-name feature model and exact, grant-only matching. The vocabulary used here is
 * deliberately made-up - the matcher must behave identically for categories it has never seen,
 * which is the whole point of keeping it string-only.
 *
 * Note what is NOT here: no Database or Storage feature. Those axes are explicit claim fields
 * checked against the running deployment, not opaque features gated at a call site.
 *
 * @author Serge
 */
@Execution(CONCURRENT)
public class FeatureMatchUtilsTest {

    private static final Set<String> GRANTED = Set.of("Cat.ALPHA", "Cat.BETA", "Other.ALPHA");

    @Test
    public void test_exactMatch() {
        assertTrue(FeatureMatchUtils.matches(GRANTED, new Feature("Cat.ALPHA")));
        assertTrue(FeatureMatchUtils.matches(GRANTED, new Feature("Other.ALPHA")));
    }

    @Test
    public void test_notGranted_similarName() {
        assertFalse(FeatureMatchUtils.matches(GRANTED, new Feature("Cat.GAMMA")));
        assertFalse(FeatureMatchUtils.matches(GRANTED, new Feature("Other.BETA")));
    }

    @Test
    public void test_notGranted_unknownName() {
        assertFalse(FeatureMatchUtils.matches(GRANTED, new Feature("Absent.ALPHA")));
        assertFalse(FeatureMatchUtils.matches(GRANTED, new Feature("NeverSeen.BETA")));
    }

    @Test
    public void test_matchIsOnTheWholeName_notASuffix() {
        // 'Cat.BETA' is granted; a name that merely shares a suffix with it must not match.
        assertFalse(FeatureMatchUtils.matches(GRANTED, new Feature("Other.BETA")));
    }

    @Test
    public void test_noWildcard() {
        // 'ANY' is an ordinary value with no special meaning - grants are always enumerated.
        final Set<String> granted = Set.of("Cat.ANY");
        assertFalse(FeatureMatchUtils.matches(granted, new Feature("Cat.ALPHA")));
        assertFalse(FeatureMatchUtils.matches(granted, new Feature("Cat.BETA")));
        assertTrue(FeatureMatchUtils.matches(granted, new Feature("Cat.ANY")));
    }

    @Test
    public void test_emptyGrantSet() {
        assertFalse(FeatureMatchUtils.matches(Set.of(), new Feature("Cat.ALPHA")));
    }

    @Test
    public void test_wireForm_isTheNameItself() {
        assertEquals("Cat.ALPHA", new Feature("Cat.ALPHA").name());
    }

    @Test
    public void test_blankNameRejected() {
        assertThrows(IllegalArgumentException.class, () -> new Feature(""));
        assertThrows(IllegalArgumentException.class, () -> new Feature(" "));
    }

    @Test
    public void test_aDotIsJustACharacter() {
        // the old model split on the dot and forbade one inside either half, so that 'A.B' + 'C'
        // and 'A' + 'B.C' could not collide on one wire form. A name is opaque now: a dot groups
        // nothing, there is nothing to collide, and any number of them is ordinary.
        assertEquals("MH.BATCH", new Feature("MH.BATCH").name());
        assertEquals("a.b.c", new Feature("a.b.c").name());
        assertEquals("PLAIN", new Feature("PLAIN").name());
    }
}
