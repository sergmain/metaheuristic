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

package ai.metaheuristic.ai.processor.event;

import ai.metaheuristic.ai.processor.TaskProcessorCoordinatorService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.springframework.context.event.EventListener;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT;

/**
 * Characterization test for the processor-side asset-prepared -> task-processing
 * hand-off latency.
 *
 * Background: a task that finished asset preparation (TaskAssetPreparer,
 * log code 951.330) waits up to one full taskProcessor scheduler tick
 * (SECONDS_9) before TaskProcessorCoordinatorService picks it up
 * (log code 415.020 / 100.001). The hand-off is DB-only with no event,
 * so a task with no real work still sits idle ~8s. See processor-lag
 * investigation 2026-05-17.
 *
 * This test pins whether TaskProcessorCoordinatorService declares an
 * {@link EventListener}-annotated method consuming {@link TaskAssetReadyEvent},
 * which would collapse that gap to near-zero.
 *
 * @author Sergio Lissner
 */
@Execution(CONCURRENT)
public class TaskAssetReadyEventListenerTest {

    private static boolean hasListenerForTaskAssetReadyEvent() {
        return Arrays.stream(TaskProcessorCoordinatorService.class.getDeclaredMethods())
                .filter(m -> m.isAnnotationPresent(EventListener.class))
                .map(Method::getParameterTypes)
                .anyMatch(params -> params.length == 1 && params[0].equals(TaskAssetReadyEvent.class));
    }

    @Test
    public void coordinatorListensForTaskAssetReadyEvent() {
        assertThat(hasListenerForTaskAssetReadyEvent()).isTrue();
    }
}
