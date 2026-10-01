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

package ai.metaheuristic.ai.dispatcher.exec_context;

import ai.metaheuristic.ai.dispatcher.event.events.ProcessDeletedExecContextEvent;
import ai.metaheuristic.ai.dispatcher.exec_context_segment.ExecContextChunkedDeletionTxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * @author Serge
 * Date: 6/26/2021
 * Time: 12:34 AM
 *
 * <p>041-EXEC-CONTEXT-SEGMENTS-PLAN Phase 21: on a deleted ExecContext, its segment and join records are deleted in
 * bounded steps right away, off the request thread (the three whole-ExecContext records this listener used to delete
 * are gone). {@code ArtifactCleanerAtDispatcher} repeats the same deletion for anything left behind.
 *
 * <p>Error code prefix: {@code 01.924.} (unique to this class).
 */
@Service
@Profile("dispatcher")
@Slf4j
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class ExecContextCleanerService {

    private final ExecContextChunkedDeletionTxService chunkedDeletionTxService;

    /** Segment / join records deleted per transaction. */
    private static final int BOUND = 100;

    @Async
    @EventListener
    public void fixedDelayExecContextRelatives(ProcessDeletedExecContextEvent event) {
        deleteSegmentsAndJoins(event);
    }

    private void deleteSegmentsAndJoins(ProcessDeletedExecContextEvent event) {
        final var kinds = java.util.EnumSet.of(ExecContextChunkedDeletionTxService.Kind.SEGMENT, ExecContextChunkedDeletionTxService.Kind.JOIN);
        try {
            while (chunkedDeletionTxService.deleteStep(event.execContextId, BOUND, kinds) != null) {
                // one bounded transaction per step
            }
        }
        catch (Throwable th) {
            log.error("01.924.010 deleting the segment and join records of ExecContext #" + event.execContextId, th);
        }
    }
}
