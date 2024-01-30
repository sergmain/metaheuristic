/*
 * Metaheuristic, Copyright (C) 2017-2024, Innovation platforms, LLC
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

package ai.metaheuristic.ai.processor.download;

import ai.metaheuristic.ai.functions.FunctionRepositoryData;
import ai.metaheuristic.ai.functions.communication.FunctionRepositoryResponseParams;
import ai.metaheuristic.ai.processor.actors.AbstractTaskQueue;
import org.junit.jupiter.api.Test;

import static ai.metaheuristic.ai.functions.FunctionEnums.DownloadPriority.NORMAL;
import static ai.metaheuristic.ai.processor.ProcessorAndCoreData.AssetManagerUrl;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Serge
 * Date: 1/2/2021
 * Time: 1:37 AM
 */
public class TestDownloadQueue {

    private static class TestQueue extends AbstractTaskQueue<FunctionRepositoryData.DownloadFunctionTask> {

    }

    @Test
    public void test() {
        TestQueue testQueue = new TestQueue();

        // value of shortFunctionConfig doesn't matter
        FunctionRepositoryData.DownloadFunctionTask task1 = new FunctionRepositoryData.DownloadFunctionTask("function-1", new FunctionRepositoryResponseParams.ShortFunctionConfig(), new AssetManagerUrl("url2"), false, NORMAL);

        testQueue.add(task1);
        assertEquals(1, testQueue.queueSize());

        FunctionRepositoryData.DownloadFunctionTask task2 = new FunctionRepositoryData.DownloadFunctionTask("function-1", new FunctionRepositoryResponseParams.ShortFunctionConfig(), new AssetManagerUrl("url2"), false, NORMAL);

        testQueue.add(task2);
        assertEquals(1, testQueue.queueSize());

        FunctionRepositoryData.DownloadFunctionTask task3 = new FunctionRepositoryData.DownloadFunctionTask("function-2", new FunctionRepositoryResponseParams.ShortFunctionConfig(), new AssetManagerUrl("url2"), false, NORMAL);

        FunctionRepositoryData.DownloadFunctionTask task4 = new FunctionRepositoryData.DownloadFunctionTask("function-1", new FunctionRepositoryResponseParams.ShortFunctionConfig(), new AssetManagerUrl("url2-1"), false, NORMAL);

        testQueue.add(task3);
        testQueue.add(task4);
        assertEquals(3, testQueue.queueSize());

    }
}
