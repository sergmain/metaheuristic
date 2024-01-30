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

package ai.metaheuristic.commons.utils.threads;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.springframework.lang.Nullable;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * @author Sergio Lissner
 * Date: 9/21/2023
 * Time: 2:12 PM
 */
public class MultiTenantedQueueTest {

    @Data
    @AllArgsConstructor
    @EqualsAndHashCode(of={"taskId"})
    public static class Event implements EventWithId<Long> {
        public final Long sourceCodeId;
        public final Long execContextId;
        public final Long taskId;

        @Override
        public Long getId() {
            return execContextId;
        }
    }


    @SneakyThrows
    @Test
    public void test() {
        System.out.println("init queue");
        MultiTenantedQueue<Long, Event> queue = new MultiTenantedQueue<>(100, Duration.ZERO, true, null, MultiTenantedQueueTest::process);

        try {


            Event event1 = new Event(1L, 10L, 20L);

            queue.putToQueueInternal(event1);

            assertEquals(1, queue.queue.size());
            assertEquals(1, queue.queue.get(10L).size());
            assertEquals(event1, queue.queue.get(10L).get(0));

            Event event2 = new Event(1L, 5L, 21L);

            queue.putToQueueInternal(event2);

            assertEquals(2, queue.queue.size());
            assertEquals(1, queue.queue.get(5L).size());

            Event event3 = new Event(1L, 5L, 22L);

            queue.putToQueueInternal(event3);

            assertEquals(2, queue.queue.size());
            assertEquals(2, queue.queue.get(5L).size());

            queue.putToQueueInternal(event3);

            assertEquals(2, queue.queue.size());
            assertEquals(2, queue.queue.get(5L).size());

            System.out.println("process queue");

            queue.processPoolOfExecutors(5L);
            Thread.sleep(100);

            QueueWithThread<Event> twe5 = queue.queue.get(5L);
            assertNotNull(twe5);
            assertNotNull(twe5.thread);
            assertEquals(1, twe5.size());

            queue.processPoolOfExecutors(10L);
            Thread.sleep(100);

            QueueWithThread<Event> twe10 = queue.queue.get(10L);
            assertNotNull(twe10);
            assertNotNull(twe10.thread);
            assertEquals(0, twe10.size());

            assertEquals(2, queue.queue.size());
            assertTrue(queue.queue.containsKey(5L));
            assertTrue(queue.queue.containsKey(10L));

            System.out.println("terminate the current thread, execContextId: 5L");
            id = 5L;
            Thread.sleep(1000);

            queue.processPoolOfExecutors(5L);
            Thread.sleep(100);

            twe5 = queue.queue.get(5L);
            assertNotNull(twe5);
            assertTrue(twe5.isEmpty());
            assertNotNull(twe5.thread);

            System.out.println("terminate the current thread, execContextId: 5L");
            id = 5L;
            Thread.sleep(1000);
            twe5 = queue.queue.get(5L);
            assertNotNull(twe5);
            assertNull(twe5.thread);
            assertTrue(twe5.isEmpty());


            twe10 = queue.queue.get(10L);
            assertNotNull(twe10);
            assertNotNull(twe10.thread);
            assertTrue(twe10.isEmpty());

            System.out.println("terminate the current thread, execContextId: 10L");
            id = 10L;
            Thread.sleep(1000);

            queue.processPoolOfExecutors(5L);


            System.out.println("Final check that everything has been completed");

            twe5 = queue.queue.get(5L);
            assertNotNull(twe5);
            assertNull(twe5.thread);
            assertTrue(twe5.isEmpty());

            twe10 = queue.queue.get(10L);
            assertNotNull(twe10);
            assertNull(twe10.thread);
            assertTrue(twe10.isEmpty());


            assertEquals(2, queue.queue.size());

            assertNotNull(queue.queue.get(5L));
            assertNotNull(queue.queue.get(10L));

            flag = false;
        }
        finally {
            queue.clearQueue();
        }
    }

    @Nullable
    private static Long id = null;
    private static boolean flag= true;

    @SneakyThrows
    private static void process(final Event event) {
        int i=0;
        while (flag) {
            if (id!=null && id.equals(event.execContextId)) {
                id = null;
                break;
            }
            Thread.sleep(500);
            System.out.println(Thread.currentThread().threadId()+", "+ Thread.currentThread().getName()+", "+event.taskId+", i = " + i++);
            if (i==100) {
                throw new RuntimeException("Too long execution");
            }
        }
        System.out.println(Thread.currentThread().threadId()+", "+event.taskId+" done. ");
    }

}
