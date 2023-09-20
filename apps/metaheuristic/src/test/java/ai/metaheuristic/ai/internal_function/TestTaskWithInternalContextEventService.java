/*
 * Metaheuristic, Copyright (C) 2017-2023, Innovation platforms, LLC
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

import ai.metaheuristic.ai.dispatcher.event.events.TaskWithInternalContextEvent;
import ai.metaheuristic.ai.dispatcher.internal_functions.TaskWithInternalContextEventService;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.springframework.lang.Nullable;

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
        try {

            System.out.println("init queue");

            TaskWithInternalContextEvent event1 = new TaskWithInternalContextEvent(1L, 10L, 20L);

            TaskWithInternalContextEventService.putToQueueInternal(event1);

            assertEquals(1, TaskWithInternalContextEventService.QUEUE.size());
            assertEquals(1, TaskWithInternalContextEventService.QUEUE.get(10L).size());
            assertEquals(event1, TaskWithInternalContextEventService.QUEUE.get(10L).get(0));

            TaskWithInternalContextEvent event2 = new TaskWithInternalContextEvent(1L, 5L, 21L);

            TaskWithInternalContextEventService.putToQueueInternal(event2);

            assertEquals(2, TaskWithInternalContextEventService.QUEUE.size());
            assertEquals(1, TaskWithInternalContextEventService.QUEUE.get(5L).size());

            TaskWithInternalContextEvent event3 = new TaskWithInternalContextEvent(1L, 5L, 22L);

            TaskWithInternalContextEventService.putToQueueInternal(event3);

            assertEquals(2, TaskWithInternalContextEventService.QUEUE.size());
            assertEquals(2, TaskWithInternalContextEventService.QUEUE.get(5L).size());

            TaskWithInternalContextEventService.putToQueueInternal(event3);

            assertEquals(2, TaskWithInternalContextEventService.QUEUE.size());
            assertEquals(2, TaskWithInternalContextEventService.QUEUE.get(5L).size());

            System.out.println("process queue");

            TaskWithInternalContextEventService.processPoolOfExecutors(5L, TestTaskWithInternalContextEventService::process);
            Thread.sleep(100);

            TaskWithInternalContextEventService.ThreadWithEvents twe5 = TaskWithInternalContextEventService.QUEUE.get(5L);
            assertNotNull(twe5);
            assertNotNull(twe5.thread);
            assertEquals(1, twe5.size());

            TaskWithInternalContextEventService.processPoolOfExecutors(10L, TestTaskWithInternalContextEventService::process);
            Thread.sleep(100);

            TaskWithInternalContextEventService.ThreadWithEvents twe10 = TaskWithInternalContextEventService.QUEUE.get(10L);
            assertNotNull(twe10);
            assertNotNull(twe10.thread);
            assertEquals(0, twe10.size());

            assertEquals(2, TaskWithInternalContextEventService.QUEUE.size());
            assertTrue(TaskWithInternalContextEventService.QUEUE.containsKey(5L));
            assertTrue(TaskWithInternalContextEventService.QUEUE.containsKey(10L));

            System.out.println("terminate the current thread, execContextId: 5L");
            id = 5L;
            Thread.sleep(1000);

            TaskWithInternalContextEventService.processPoolOfExecutors(5L, TestTaskWithInternalContextEventService::process);
            Thread.sleep(100);

            twe5 = TaskWithInternalContextEventService.QUEUE.get(5L);
            assertNotNull(twe5);
            assertTrue(twe5.isEmpty());
            assertNotNull(twe5.thread);

            System.out.println("terminate the current thread, execContextId: 5L");
            id = 5L;
            Thread.sleep(1000);
            twe5 = TaskWithInternalContextEventService.QUEUE.get(5L);
            assertNotNull(twe5);
            assertNull(twe5.thread);
            assertTrue(twe5.isEmpty());


            twe10 = TaskWithInternalContextEventService.QUEUE.get(10L);
            assertNotNull(twe10);
            assertNotNull(twe10.thread);
            assertTrue(twe10.isEmpty());

            System.out.println("terminate the current thread, execContextId: 10L");
            id = 10L;
            Thread.sleep(1000);

            TaskWithInternalContextEventService.processPoolOfExecutors(5L, TestTaskWithInternalContextEventService::process);


            System.out.println("Final check that everything has been completed");

            twe5 = TaskWithInternalContextEventService.QUEUE.get(5L);
            assertNotNull(twe5);
            assertNull(twe5.thread);
            assertTrue(twe5.isEmpty());

            twe10 = TaskWithInternalContextEventService.QUEUE.get(10L);
            assertNotNull(twe10);
            assertNull(twe10.thread);
            assertTrue(twe10.isEmpty());


            assertEquals(2, TaskWithInternalContextEventService.QUEUE.size());

            assertNotNull(TaskWithInternalContextEventService.QUEUE.get(5L));
            assertNotNull(TaskWithInternalContextEventService.QUEUE.get(10L));

            flag = false;
        }
        finally {
            TaskWithInternalContextEventService.clearQueue();
        }
    }

    @Nullable
    private static Long id = null;
    private static boolean flag= true;

    @SneakyThrows
    private static void process(final TaskWithInternalContextEvent event) {
        int i=0;
        while (flag) {
            if (id!=null && id.equals(event.execContextId)) {
                id = null;
                break;
            }
            Thread.sleep(500);
            System.out.println(Thread.currentThread().getId()+", "+ Thread.currentThread().getName()+", "+event.taskId+", i = " + i++);
            if (i==100) {
                throw new RuntimeException("Too long execution");
            }
        }
        System.out.println(Thread.currentThread().getId()+", "+event.taskId+" done. ");
    }
}
