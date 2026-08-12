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
 * @author Serge
 */
@Execution(CONCURRENT)
public class FeatureMatchUtilsTest {

    private static final Set<String> GRANTED = Set.of("Capability:RG", "Capability:LEGAL", "Database:H2");

    @Test
    public void test_exactMatch() {
        assertTrue(FeatureMatchUtils.matches(GRANTED, new Feature("Capability", "RG")));
        assertTrue(FeatureMatchUtils.matches(GRANTED, new Feature("Database", "H2")));
    }

    @Test
    public void test_notGranted_sameCategory() {
        assertFalse(FeatureMatchUtils.matches(GRANTED, new Feature("Capability", "BATCH")));
        assertFalse(FeatureMatchUtils.matches(GRANTED, new Feature("Database", "POSTGRES")));
    }

    @Test
    public void test_notGranted_unknownCategory() {
        assertFalse(FeatureMatchUtils.matches(GRANTED, new Feature("Storage", "S3")));
        assertFalse(FeatureMatchUtils.matches(GRANTED, new Feature("NeverSeen", "RG")));
    }

    @Test
    public void test_valueDoesNotLeakAcrossCategories() {
        // 'RG' is granted as a Capability; the same value in another category must not match.
        assertFalse(FeatureMatchUtils.matches(GRANTED, new Feature("Storage", "RG")));
    }

    @Test
    public void test_noWildcard() {
        // 'ANY' is an ordinary value with no special meaning - grants are always enumerated.
        final Set<String> granted = Set.of("Database:ANY");
        assertFalse(FeatureMatchUtils.matches(granted, new Feature("Database", "H2")));
        assertFalse(FeatureMatchUtils.matches(granted, new Feature("Database", "POSTGRES")));
        assertTrue(FeatureMatchUtils.matches(granted, new Feature("Database", "ANY")));
    }

    @Test
    public void test_emptyGrantSet() {
        assertFalse(FeatureMatchUtils.matches(Set.of(), new Feature("Capability", "RG")));
    }

    @Test
    public void test_key_wireForm() {
        assertEquals("Capability:RG", new Feature("Capability", "RG").key());
    }

    @Test
    public void test_blankComponentsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new Feature("", "RG"));
        assertThrows(IllegalArgumentException.class, () -> new Feature("Capability", " "));
    }

    @Test
    public void test_separatorInComponentsRejected() {
        // otherwise 'A:B' + 'C' and 'A' + 'B:C' would collide on the same wire form.
        assertThrows(IllegalArgumentException.class, () -> new Feature("Cap:ability", "RG"));
        assertThrows(IllegalArgumentException.class, () -> new Feature("Capability", "R:G"));
    }
}
