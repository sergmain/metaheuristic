/*
 * Metaheuristic, Copyright (C) 2017-2026, Innovation platforms, LLC
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

package ai.metaheuristic.ai;

import ai.metaheuristic.ai.processor.actors.DownloadSealedSecretService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.SchedulingConfigurer;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Which processor scheduler drains the sealed-secret fetch queue.
 *
 * <p>A Processor actor is an {@code AbstractTaskQueue}: something enqueues work, and only its
 * {@code process()} ever takes it out again. Each actor is driven by its own {@code SchedulingConfigurer}
 * nested in {@link Schedulers}. An actor that no scheduler holds is fed forever and never drained - which is
 * what {@code DownloadSealedSecretService} was: {@code TaskProcessor} enqueued a fetch on every launch cycle
 * ({@code 100.140}), no fetch was ever sent (not one {@code 812.} line), and a Task whose Function declares
 * {@code api.keyCode} stayed AWAITING forever.
 *
 * <p>Structural on purpose: at runtime the wiring cannot be observed in a test - every scheduled method and
 * {@code process()} itself return at once under {@code globals.testing}.
 *
 * @author Sergio Lissner
 * Date: 9/17/2026
 */
@Execution(ExecutionMode.CONCURRENT)
public class SchedulersProcessorActorsTest {

    @Test
    public void test_theSealedSecretFetchQueueIsDrainedByAProcessorScheduler() {
        final List<String> holders = Arrays.stream(Schedulers.class.getDeclaredClasses())
                .filter(SchedulingConfigurer.class::isAssignableFrom)
                .filter(SchedulersProcessorActorsTest::isProcessorProfile)
                .filter(c -> Arrays.stream(c.getDeclaredFields()).anyMatch(f -> f.getType() == DownloadSealedSecretService.class))
                .map(Class::getSimpleName)
                .toList();

        assertEquals(1, holders.size(),
                "processor schedulers holding the sealed-secret fetch actor: " + holders);
    }

    private static boolean isProcessorProfile(Class<?> c) {
        final Profile profile = c.getAnnotation(Profile.class);
        return profile != null && Arrays.asList(profile.value()).contains("processor");
    }
}
