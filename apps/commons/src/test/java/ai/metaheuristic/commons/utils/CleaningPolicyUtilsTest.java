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

package ai.metaheuristic.commons.utils;

import ai.metaheuristic.api.EnumsApi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import static ai.metaheuristic.api.EnumsApi.CleaningPolicy.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * cleaningPolicy as a comma-separated list: parsing, the canonical form, and when a policy is due.
 */
@Execution(ExecutionMode.CONCURRENT)
public class CleaningPolicyUtilsTest {

    private static final long DAY = Duration.ofDays(1).toMillis();
    private static final long FINISHED_ON = 1_800_000_000_000L;

    @Test
    public void test_policies_targetAndTimeout() {
        assertEquals(EnumsApi.CleaningTarget.TASK_DIR, ALL.target);
        assertEquals(Duration.ZERO, ALL.timeout);
        assertEquals(EnumsApi.CleaningTarget.ASSET_DIR, ASSETS.target);
        assertEquals(Duration.ZERO, ASSETS.timeout);
        assertEquals(EnumsApi.CleaningTarget.TASK_DIR, FULL_AFTER_ONE_WEEK.target, "FULL_AFTER_ONE_WEEK deletes what ALL deletes");
        assertEquals(Duration.ofDays(7), FULL_AFTER_ONE_WEEK.timeout);
    }

    @Test
    public void test_parse_list() {
        assertEquals(EnumSet.of(ASSETS, FULL_AFTER_ONE_WEEK), CleaningPolicyUtils.parseStrict("ASSETS, FULL_AFTER_ONE_WEEK"));
        assertEquals(EnumSet.of(ASSETS, FULL_AFTER_ONE_WEEK), CleaningPolicyUtils.parseStrict("FULL_AFTER_ONE_WEEK,ASSETS"));
        assertEquals(EnumSet.of(ALL), CleaningPolicyUtils.parseStrict("ALL"));
    }

    @Test
    public void test_parse_blankItemsAndRepeats() {
        assertEquals(EnumSet.of(ASSETS), CleaningPolicyUtils.parseStrict(" ASSETS ,, ASSETS , "));
    }

    @Test
    public void test_parse_nothingDeclared() {
        assertTrue(CleaningPolicyUtils.parseStrict(null).isEmpty());
        assertTrue(CleaningPolicyUtils.parseStrict("").isEmpty());
        assertTrue(CleaningPolicyUtils.parseStrict("  ").isEmpty());
        assertTrue(CleaningPolicyUtils.parseStrict(" , ").isEmpty());
    }

    @Test
    public void test_parseStrict_unknownName_isRefused() {
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> CleaningPolicyUtils.parseStrict("ASSETS, assets"));
        assertTrue(e.getMessage().startsWith("01.329.020 "), e.getMessage());
        assertTrue(e.getMessage().contains("'assets'"), "names are case-sensitive, as enum names: " + e.getMessage());
    }

    @Test
    public void test_parse_lenient_skipsUnknownAndKeepsTheRest() {
        final List<String> unknown = new ArrayList<>();
        assertEquals(EnumSet.of(ASSETS, FULL_AFTER_ONE_WEEK),
                CleaningPolicyUtils.parse("ASSETS, FUTURE_POLICY, FULL_AFTER_ONE_WEEK", unknown::add));
        assertEquals(List.of("FUTURE_POLICY"), unknown);
    }

    @Test
    public void test_asString_canonical() {
        assertEquals("ASSETS, FULL_AFTER_ONE_WEEK", CleaningPolicyUtils.asString(EnumSet.of(FULL_AFTER_ONE_WEEK, ASSETS)));
        assertEquals("ALL, ASSETS, FULL_AFTER_ONE_WEEK", CleaningPolicyUtils.asString(EnumSet.allOf(EnumsApi.CleaningPolicy.class)));
        assertNull(CleaningPolicyUtils.asString(EnumSet.noneOf(EnumsApi.CleaningPolicy.class)));
    }

    @Test
    public void test_asString_parse_roundTrip() {
        for (EnumsApi.CleaningPolicy p : EnumsApi.CleaningPolicy.values()) {
            final EnumSet<EnumsApi.CleaningPolicy> set = EnumSet.of(p, ASSETS);
            assertEquals(set, CleaningPolicyUtils.parseStrict(CleaningPolicyUtils.asString(set)), p.name());
        }
    }

    @Test
    public void test_isDue_zeroTimeout_atOnce() {
        assertTrue(CleaningPolicyUtils.isDue(ALL, FINISHED_ON, FINISHED_ON));
        assertTrue(CleaningPolicyUtils.isDue(ASSETS, FINISHED_ON, FINISHED_ON));
        assertTrue(CleaningPolicyUtils.isDue(ASSETS, null, FINISHED_ON), "ALL and ASSETS never needed finishedOn");
    }

    @Test
    public void test_isDue_oneWeek() {
        assertFalse(CleaningPolicyUtils.isDue(FULL_AFTER_ONE_WEEK, FINISHED_ON, FINISHED_ON));
        assertFalse(CleaningPolicyUtils.isDue(FULL_AFTER_ONE_WEEK, FINISHED_ON, FINISHED_ON + 7 * DAY - 1));
        assertTrue(CleaningPolicyUtils.isDue(FULL_AFTER_ONE_WEEK, FINISHED_ON, FINISHED_ON + 7 * DAY));
        assertTrue(CleaningPolicyUtils.isDue(FULL_AFTER_ONE_WEEK, FINISHED_ON, FINISHED_ON + 30 * DAY));
    }

    @Test
    public void test_isDue_withTimeout_neverWithoutFinishedOn() {
        assertFalse(CleaningPolicyUtils.isDue(FULL_AFTER_ONE_WEEK, null, FINISHED_ON + 30 * DAY));
    }
}
