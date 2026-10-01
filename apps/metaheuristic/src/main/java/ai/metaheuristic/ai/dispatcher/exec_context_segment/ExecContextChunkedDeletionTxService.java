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

package ai.metaheuristic.ai.dispatcher.exec_context_segment;

import ai.metaheuristic.ai.dispatcher.repositories.ExecContextJoinRepository;
import ai.metaheuristic.ai.dispatcher.repositories.ExecContextRepository;
import ai.metaheuristic.ai.dispatcher.repositories.ExecContextSegmentRepository;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.ai.dispatcher.repositories.VariableRepository;
import ai.metaheuristic.ai.utils.TxUtils;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Deletes the records of a deleted ExecContext in bounded steps (041-EXEC-CONTEXT-SEGMENTS-PLAN, Phase 14; decision 3: no
 * transaction spans all segments, Tasks or Variables of one ExecContext - deleting an ExecContext with its Tasks and
 * Variables in one transaction exhausted the MySQL redo log).
 *
 * <p>One call of {@link #deleteStep} is one transaction and deletes at most {@code bound} records of ONE kind: the first
 * of the requested kinds, in the order segments, join records, Tasks, Variables, that still has records of the
 * ExecContext. Each step re-reads what is left, so stopping after any step and calling again later resumes where the
 * deletion stopped. Only a deleted ExecContext's records are deleted: the ExecContext record must already be gone.
 *
 * <p>Error code prefix: {@code 01.922.} (unique to this class).
 */
@Service
@Profile("dispatcher")
@RequiredArgsConstructor(onConstructor_ = {@Autowired})
public class ExecContextChunkedDeletionTxService {

    public enum Kind {SEGMENT, JOIN, TASK, VARIABLE}

    public static final Set<Kind> ALL = EnumSet.allOf(Kind.class);

    /** What one step deleted. */
    public record Step(Kind kind, int deleted) {
    }

    private final ExecContextRepository execContextRepository;
    private final ExecContextSegmentRepository segmentRepository;
    private final ExecContextJoinRepository joinRepository;
    private final TaskRepository taskRepository;
    private final VariableRepository variableRepository;

    /**
     * Deletes at most {@code bound} records of the first kind in {@code kinds} (in {@link Kind} order) that the deleted
     * ExecContext {@code execContextId} still has; null when none of those kinds has a record left.
     */
    @Nullable
    @Transactional
    public Step deleteStep(Long execContextId, int bound, Set<Kind> kinds) {
        TxUtils.checkTxExists();
        if (bound < 1) {
            throw new IllegalArgumentException("01.922.010 bound must be positive, was " + bound);
        }
        if (execContextRepository.findIdById(execContextId) != null) {
            throw new IllegalStateException("01.922.020 ExecContext #" + execContextId
                    + " still exists - only the records of a deleted ExecContext are deleted");
        }
        final Pageable page = PageRequest.of(0, bound);
        for (Kind kind : Kind.values()) {
            if (!kinds.contains(kind)) {
                continue;
            }
            final List<Long> ids = switch (kind) {
                case SEGMENT -> segmentRepository.findIdsPageByExecContextId(page, execContextId);
                case JOIN -> joinRepository.findIdsPageByExecContextId(page, execContextId);
                case TASK -> taskRepository.findAllByExecContextId(page, execContextId);
                case VARIABLE -> variableRepository.findAllByExecContextId(page, execContextId);
            };
            if (ids.isEmpty()) {
                continue;
            }
            switch (kind) {
                case SEGMENT -> segmentRepository.deleteByIds(ids);
                case JOIN -> joinRepository.deleteByIds(ids);
                case TASK -> taskRepository.deleteByIds(ids);
                case VARIABLE -> variableRepository.deleteByIds(ids);
            }
            return new Step(kind, ids.size());
        }
        return null;
    }
}
