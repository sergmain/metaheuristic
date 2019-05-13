/*
 * Metaheuristic, Copyright (C) 2017-2019  Serge Maslyukov
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

package ai.metaheuristic.ai.station.actors;

import ai.metaheuristic.ai.station.tasks.DownloadResourceTask;
import org.junit.Test;

import java.io.File;

import static org.junit.Assert.*;

public class TestAbstractTaskQueue {

    public static class SimpleClass extends AbstractTaskQueue<DownloadResourceTask> {
    }

    @Test
    public void test() {
        SimpleClass actor = new SimpleClass();

        DownloadResourceTask task = new DownloadResourceTask("resource-id-01", 10, new File("aaa"));

        actor.add(task);
        assertEquals(1, actor.queueSize());

        DownloadResourceTask task1 = new DownloadResourceTask("resource-id-01", 10, new File("bbb"));
        actor.add(task1);
        assertEquals(1, actor.queueSize());

        DownloadResourceTask t1 = actor.poll();
        assertNotNull(t1);

        DownloadResourceTask t2 = actor.poll();
        assertNull(t2);

        DownloadResourceTask task2 = new DownloadResourceTask("resource-id-02", 10, new File("."));
        actor.add(task1);
        actor.add(task2);
        assertEquals(2, actor.queueSize());

        DownloadResourceTask task3 = new DownloadResourceTask("resource-id-02", 11, new File("."));
        actor.add(task3);
        assertEquals(3, actor.queueSize());

    }
}
