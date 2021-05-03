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
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Serge
 * Date: 5/2/2021
 * Time: 9:24 PM
 */
public class TestTaskWithInternalContextEventService {

    @SuppressWarnings("ConstantConditions")
    @Test
    public void testConstants() {
        assertTrue(TaskWithInternalContextEventService.MAX_QUEUE_SIZE > TaskWithInternalContextEventService.MAX_ACTIVE_THREAD * 5);
    }

    @Test
    public void test() {
        TaskWithInternalContextEvent event1 = new TaskWithInternalContextEvent(1L, 10L, 20L);

        TaskWithInternalContextEventService.putToQueueInternal(event1);

        assertEquals(1, TaskWithInternalContextEventService.queue.size());
        assertEquals(event1, TaskWithInternalContextEventService.queue.get(0));

        TaskWithInternalContextEvent event2 = new TaskWithInternalContextEvent(1L, 5L, 21L);

        TaskWithInternalContextEventService.putToQueueInternal(event2);

        assertEquals(2, TaskWithInternalContextEventService.queue.size());
        // TODO 2021-05-02 sorting was turned off
//        assertEquals(event2, TaskWithInternalContextEventService.queue.get(0));
//        assertEquals(event1, TaskWithInternalContextEventService.queue.get(1));

        TaskWithInternalContextEvent event3 = new TaskWithInternalContextEvent(1L, 5L, 22L);

        TaskWithInternalContextEventService.putToQueueInternal(event3);

        assertEquals(3, TaskWithInternalContextEventService.queue.size());
        // TODO 2021-05-02 sorting was turned off
//        assertEquals(5L, TaskWithInternalContextEventService.queue.get(0).execContextId);
//        assertEquals(5L, TaskWithInternalContextEventService.queue.get(1).execContextId);
//        assertEquals(10L, TaskWithInternalContextEventService.queue.get(2).execContextId);

        TaskWithInternalContextEventService.putToQueueInternal(event3);

        assertEquals(3, TaskWithInternalContextEventService.queue.size());

    }
}
