/*
 * Metaheuristic, Copyright (C) 2017-2021, Innovation platforms, LLC
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

import ai.metaheuristic.ai.processor.actors.AbstractTaskQueue;
import ai.metaheuristic.ai.processor.tasks.DownloadFunctionTask;
import org.junit.jupiter.api.Test;

import static ai.metaheuristic.ai.processor.ProcessorAndCoreData.AssetManagerUrl;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Serge
 * Date: 1/2/2021
 * Time: 1:37 AM
 */
public class TestDownloadQueue {

    private static class TestQueue extends AbstractTaskQueue<DownloadFunctionTask> {

    }

    @Test
    public void test() {
        TestQueue testQueue = new TestQueue();

        DownloadFunctionTask task1 = new DownloadFunctionTask("function-1", new AssetManagerUrl("url2"));

        testQueue.add(task1);
        assertEquals(1, testQueue.queueSize());

        DownloadFunctionTask task2 = new DownloadFunctionTask("function-1", new AssetManagerUrl("url2"));

        testQueue.add(task2);
        assertEquals(1, testQueue.queueSize());

        DownloadFunctionTask task3 = new DownloadFunctionTask("function-2", new AssetManagerUrl("url2"));

        DownloadFunctionTask task4 = new DownloadFunctionTask("function-1", new AssetManagerUrl("url2-1"));

        testQueue.add(task3);
        testQueue.add(task4);
        assertEquals(3, testQueue.queueSize());

    }
}
