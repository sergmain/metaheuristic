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

package ai.metaheuristic.ai.processor.actors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.springframework.context.ApplicationEventPublisher;

import java.lang.reflect.Field;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT;

/**
 * Characterization test for the processor-side variable-loaded -> asset-preparing
 * hand-off latency.
 *
 * Background: when DownloadVariableService finishes loading a task's last
 * variable (log "Variable #N was loaded"), nothing notifies TaskAssetPreparer.
 * TaskAssetPreparer re-scans only on its own taskAssigner scheduler tick
 * (SECONDS_5), so the variable-loaded -> 951.330 transition idles ~3s.
 *
 * TaskAssetPreparer already exposes an @Async @EventListener consuming
 * AssetPreparingForProcessorTaskEvent. The fix is for DownloadVariableService
 * to publish that existing event on completion; the structural prerequisite is
 * an ApplicationEventPublisher dependency, which this test pins.
 *
 * See processor-lag investigation 2026-05-17.
 *
 * @author Sergio Lissner
 */
@Execution(CONCURRENT)
public class DownloadVariableServiceEventTest {

    private static boolean hasApplicationEventPublisherField() {
        return Arrays.stream(DownloadVariableService.class.getDeclaredFields())
                .map(Field::getType)
                .anyMatch(t -> t.equals(ApplicationEventPublisher.class));
    }

    @Test
    public void downloadVariableServiceCanPublishEvents() {
        assertThat(hasApplicationEventPublisherField()).isTrue();
    }
}
