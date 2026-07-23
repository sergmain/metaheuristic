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

import ai.metaheuristic.api.EnumsApi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;

import static ai.metaheuristic.ai.processor.ArtifactCleanerAtProcessor.isAssetDirCleaningRequired;
import static ai.metaheuristic.ai.processor.ArtifactCleanerAtProcessor.isTaskDirCleaningRequired;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT;

/**
 * Decision table of Processor-side cleaning:
 * SourceCode's 'clean' and Function's cleaningPolicy==ALL are equivalent,
 * cleaningPolicy==ASSETS touches the 'asset' sub-dir only.
 */
@Execution(CONCURRENT)
public class ArtifactCleanerAtProcessorTest {

    @Test
    public void test_taskDirCleaning_clean() {
        assertTrue(isTaskDirCleaningRequired(true, null, true));
    }

    @Test
    public void test_taskDirCleaning_cleaningPolicyAll() {
        assertTrue(isTaskDirCleaningRequired(false, EnumsApi.CleaningPolicy.ALL, true));
    }

    @Test
    public void test_taskDirCleaning_notDone() {
        assertFalse(isTaskDirCleaningRequired(true, EnumsApi.CleaningPolicy.ALL, false));
    }

    @Test
    public void test_taskDirCleaning_cleaningPolicyAssets() {
        assertFalse(isTaskDirCleaningRequired(false, EnumsApi.CleaningPolicy.ASSETS, true));
    }

    @Test
    public void test_taskDirCleaning_nothingSpecified() {
        assertFalse(isTaskDirCleaningRequired(false, null, true));
    }

    @Test
    public void test_assetDirCleaning_cleaningPolicyAssets() {
        assertTrue(isAssetDirCleaningRequired(EnumsApi.CleaningPolicy.ASSETS, true));
    }

    @Test
    public void test_assetDirCleaning_notDone() {
        assertFalse(isAssetDirCleaningRequired(EnumsApi.CleaningPolicy.ASSETS, false));
    }

    @Test
    public void test_assetDirCleaning_cleaningPolicyAll() {
        assertFalse(isAssetDirCleaningRequired(EnumsApi.CleaningPolicy.ALL, true));
    }

    @Test
    public void test_assetDirCleaning_nothingSpecified() {
        assertFalse(isAssetDirCleaningRequired(null, true));
    }
}
