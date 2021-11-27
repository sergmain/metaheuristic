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

package ai.metaheuristic.ai.internal_function;

import ai.metaheuristic.ai.dispatcher.event.TaskWithInternalContextEvent;
import ai.metaheuristic.ai.dispatcher.internal_functions.TaskWithInternalContextEventService;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Serge
 * Date: 5/2/2021
 * Time: 9:24 PM
 */
public class TestTaskWithInternalContextEventService {

    @SneakyThrows
    @Test
    public void test() {
        TaskWithInternalContextEvent event1 = new TaskWithInternalContextEvent(1L, 10L, 20L);

        TaskWithInternalContextEventService.putToQueueInternal(event1);

        assertEquals(1, TaskWithInternalContextEventService.QUEUE.size());
        assertEquals(1, TaskWithInternalContextEventService.QUEUE.get(event1.execContextId).size());
        assertEquals(event1, TaskWithInternalContextEventService.QUEUE.get(event1.execContextId).get(0));

        TaskWithInternalContextEvent event2 = new TaskWithInternalContextEvent(1L, 5L, 21L);

        TaskWithInternalContextEventService.putToQueueInternal(event2);

        assertEquals(2, TaskWithInternalContextEventService.QUEUE.size());
        assertEquals(1, TaskWithInternalContextEventService.QUEUE.get(event2.execContextId).size());

        TaskWithInternalContextEvent event3 = new TaskWithInternalContextEvent(1L, 5L, 22L);

        TaskWithInternalContextEventService.putToQueueInternal(event3);

        assertEquals(2, TaskWithInternalContextEventService.QUEUE.size());
        assertEquals(2, TaskWithInternalContextEventService.QUEUE.get(event3.execContextId).size());

        TaskWithInternalContextEventService.putToQueueInternal(event3);

        assertEquals(2, TaskWithInternalContextEventService.QUEUE.size());
        assertEquals(2, TaskWithInternalContextEventService.QUEUE.get(event3.execContextId).size());

        TaskWithInternalContextEventService.pokeExecutor(5L, TestTaskWithInternalContextEventService::process);
        assertTrue(Arrays.stream(TaskWithInternalContextEventService.POOL_OF_EXECUTORS).sequential().filter(Objects::nonNull).anyMatch(o->o.execContextId.equals(5L)));

        TaskWithInternalContextEventService.pokeExecutor(10L, TestTaskWithInternalContextEventService::process);
        assertTrue(Arrays.stream(TaskWithInternalContextEventService.POOL_OF_EXECUTORS).sequential().filter(Objects::nonNull).anyMatch(o->o.execContextId.equals(10L)));

        assertEquals(2, TaskWithInternalContextEventService.QUEUE.size());
        assertTrue(TaskWithInternalContextEventService.QUEUE.containsKey(5L));
        assertTrue(TaskWithInternalContextEventService.QUEUE.containsKey(10L));

        Thread.sleep(100);
        assertEquals(1, TaskWithInternalContextEventService.QUEUE.get(5L).size());
        assertEquals(0, TaskWithInternalContextEventService.QUEUE.get(10L).size());

        int i=0;
        id = 5L;
        Thread.sleep(1000);
        TaskWithInternalContextEventService.processPoolOfExecutors(5L, TestTaskWithInternalContextEventService::process);

        assertFalse(Arrays.stream(TaskWithInternalContextEventService.POOL_OF_EXECUTORS).sequential().filter(Objects::nonNull).anyMatch(o->o.execContextId.equals(5L)));
        assertTrue(Arrays.stream(TaskWithInternalContextEventService.POOL_OF_EXECUTORS).sequential().filter(Objects::nonNull).anyMatch(o->o.execContextId.equals(10L)));

        assertEquals(1, TaskWithInternalContextEventService.QUEUE.size());

        assertNull(TaskWithInternalContextEventService.QUEUE.get(5L));
        assertEquals(0, TaskWithInternalContextEventService.QUEUE.get(10L).size());

        id = 10L;
        Thread.sleep(1000);
        TaskWithInternalContextEventService.processPoolOfExecutors(5L, TestTaskWithInternalContextEventService::process);

        assertFalse(Arrays.stream(TaskWithInternalContextEventService.POOL_OF_EXECUTORS).sequential().filter(Objects::nonNull).anyMatch(o->o.execContextId.equals(5L)));
        assertFalse(Arrays.stream(TaskWithInternalContextEventService.POOL_OF_EXECUTORS).sequential().filter(Objects::nonNull).anyMatch(o->o.execContextId.equals(10L)));

        assertEquals(0, TaskWithInternalContextEventService.QUEUE.size());

        assertNull(TaskWithInternalContextEventService.QUEUE.get(5L));
        assertNull(TaskWithInternalContextEventService.QUEUE.get(10L));

        flag = false;
    }

    private static Long id = null;
    private static boolean flag= true;

    @SneakyThrows
    private static void process(final TaskWithInternalContextEvent event) {
        int i=0;
        while (flag) {
            if (id!=null && id.equals(event.execContextId)) {
                break;
            }
            Thread.sleep(500);
            System.out.println(Thread.currentThread().getId()+", "+event.taskId+", i = " + i++);
        }
        System.out.println(Thread.currentThread().getId()+", "+event.taskId+" done. ");
    }
}
