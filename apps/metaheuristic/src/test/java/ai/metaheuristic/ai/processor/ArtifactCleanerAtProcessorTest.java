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

package ai.metaheuristic.ai.processor;

import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.yaml.processor_task.ProcessorCoreTask;
import ai.metaheuristic.api.EnumsApi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;

import java.time.Duration;
import java.util.EnumSet;
import java.util.Set;

import static ai.metaheuristic.ai.processor.ArtifactCleanerAtProcessor.cleaningAction;
import static ai.metaheuristic.ai.processor.ArtifactCleanerAtProcessor.cleaningPoliciesOf;
import static ai.metaheuristic.ai.processor.ArtifactCleanerAtProcessor.isAssetDirCleaningRequired;
import static ai.metaheuristic.ai.processor.ArtifactCleanerAtProcessor.isTaskDirCleaningRequired;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT;

/**
 * Decision table of Processor-side cleaning:
 * SourceCode's 'clean' and Function's cleaningPolicy==ALL are equivalent,
 * cleaningPolicy==ASSETS touches the 'asset' sub-dir only.
 * FULL_AFTER_ONE_WEEK is equivalent to ALL a week after the Task's finishedOn, and not before.
 */
@Execution(CONCURRENT)
public class ArtifactCleanerAtProcessorTest {

    private static final long FINISHED_ON = 1_800_000_000_000L;
    private static final long WEEK = Duration.ofDays(7).toMillis();
    private static final Set<EnumsApi.CleaningPolicy> NONE = EnumSet.noneOf(EnumsApi.CleaningPolicy.class);
    private static final Set<EnumsApi.CleaningPolicy> ALL = EnumSet.of(EnumsApi.CleaningPolicy.ALL);
    private static final Set<EnumsApi.CleaningPolicy> ASSETS = EnumSet.of(EnumsApi.CleaningPolicy.ASSETS);
    private static final Set<EnumsApi.CleaningPolicy> ASSETS_AND_WEEK = EnumSet.of(EnumsApi.CleaningPolicy.ASSETS, EnumsApi.CleaningPolicy.FULL_AFTER_ONE_WEEK);

    @Test
    public void test_taskDirCleaning_clean() {
        assertTrue(isTaskDirCleaningRequired(true, NONE, true, FINISHED_ON, FINISHED_ON));
    }

    @Test
    public void test_taskDirCleaning_cleaningPolicyAll() {
        assertTrue(isTaskDirCleaningRequired(false, ALL, true, FINISHED_ON, FINISHED_ON));
    }

    @Test
    public void test_taskDirCleaning_notDone() {
        assertFalse(isTaskDirCleaningRequired(true, ALL, false, FINISHED_ON, FINISHED_ON));
    }

    @Test
    public void test_taskDirCleaning_cleaningPolicyAssets() {
        assertFalse(isTaskDirCleaningRequired(false, ASSETS, true, FINISHED_ON, FINISHED_ON));
    }

    @Test
    public void test_taskDirCleaning_nothingSpecified() {
        assertFalse(isTaskDirCleaningRequired(false, NONE, true, FINISHED_ON, FINISHED_ON));
    }

    @Test
    public void test_assetDirCleaning_cleaningPolicyAssets() {
        assertTrue(isAssetDirCleaningRequired(ASSETS, true, FINISHED_ON, FINISHED_ON));
    }

    @Test
    public void test_assetDirCleaning_notDone() {
        assertFalse(isAssetDirCleaningRequired(ASSETS, false, FINISHED_ON, FINISHED_ON));
    }

    @Test
    public void test_assetDirCleaning_cleaningPolicyAll() {
        assertFalse(isAssetDirCleaningRequired(ALL, true, FINISHED_ON, FINISHED_ON));
    }

    @Test
    public void test_assetDirCleaning_nothingSpecified() {
        assertFalse(isAssetDirCleaningRequired(NONE, true, FINISHED_ON, FINISHED_ON));
    }

    @Test
    public void test_fullAfterOneWeek_beforeTheWeek_onlyAssets() {
        assertFalse(isTaskDirCleaningRequired(false, ASSETS_AND_WEEK, true, FINISHED_ON, FINISHED_ON + WEEK - 1));
        assertTrue(isAssetDirCleaningRequired(ASSETS_AND_WEEK, true, FINISHED_ON, FINISHED_ON),
                "ASSETS of the list is applied at once, the week doesn't delay it");
    }

    @Test
    public void test_fullAfterOneWeek_afterTheWeek_wholeDir() {
        assertTrue(isTaskDirCleaningRequired(false, ASSETS_AND_WEEK, true, FINISHED_ON, FINISHED_ON + WEEK));
    }

    @Test
    public void test_fullAfterOneWeek_notDone_nothing() {
        assertFalse(isTaskDirCleaningRequired(false, ASSETS_AND_WEEK, false, FINISHED_ON, FINISHED_ON + 30 * WEEK));
        assertFalse(isAssetDirCleaningRequired(ASSETS_AND_WEEK, false, FINISHED_ON, FINISHED_ON + 30 * WEEK));
    }

    @Test
    public void test_fullAfterOneWeek_noFinishedOn_nothing() {
        assertFalse(isTaskDirCleaningRequired(false, ASSETS_AND_WEEK, true, null, FINISHED_ON + 30 * WEEK));
    }

    private static ProcessorCoreTask task(boolean clean, String cleaningPolicy, boolean done) {
        final ProcessorCoreTask t = new ProcessorCoreTask();
        t.taskId = 42L;
        t.clean = clean;
        t.cleaningPolicy = cleaningPolicy;
        t.finishedOn = FINISHED_ON;
        t.delivered = done;
        t.completed = done;
        return t;
    }

    @Test
    public void test_cleaningAction() {
        assertEquals(Enums.TaskCleaningAction.ASSET_DIR, cleaningAction(task(false, "ASSETS, FULL_AFTER_ONE_WEEK", true), FINISHED_ON));
        assertEquals(Enums.TaskCleaningAction.TASK_DIR, cleaningAction(task(false, "ASSETS, FULL_AFTER_ONE_WEEK", true), FINISHED_ON + WEEK));
        assertEquals(Enums.TaskCleaningAction.NONE, cleaningAction(task(false, "FULL_AFTER_ONE_WEEK", true), FINISHED_ON + WEEK - 1));
        assertEquals(Enums.TaskCleaningAction.TASK_DIR, cleaningAction(task(true, null, true), FINISHED_ON));
        assertEquals(Enums.TaskCleaningAction.TASK_DIR, cleaningAction(task(false, "ALL", true), FINISHED_ON));
        assertEquals(Enums.TaskCleaningAction.NONE, cleaningAction(task(false, "ALL", false), FINISHED_ON + WEEK));
        assertEquals(Enums.TaskCleaningAction.NONE, cleaningAction(task(false, null, true), FINISHED_ON + WEEK));
    }

    @Test
    public void test_cleaningPoliciesOf_unknownNameIsSkipped() {
        assertEquals(ASSETS_AND_WEEK, cleaningPoliciesOf(task(false, "ASSETS, FUTURE_POLICY, FULL_AFTER_ONE_WEEK", true)));
        assertEquals(Enums.TaskCleaningAction.TASK_DIR, cleaningAction(task(false, "FUTURE_POLICY, FULL_AFTER_ONE_WEEK", true), FINISHED_ON + WEEK),
                "a name this Processor doesn't know doesn't cancel the rest of the list");
    }
}
