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

import ai.metaheuristic.ai.dispatcher.beans.ExecContextSegment;
import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.ai.dispatcher.event.EventPublisherService;
import ai.metaheuristic.ai.dispatcher.event.events.CheckTaskCanBeFinishedTxEvent;
import ai.metaheuristic.ai.dispatcher.event.events.InputVariablesInitedEvent;
import ai.metaheuristic.ai.dispatcher.event.events.VariableUploadedEvent;
import ai.metaheuristic.ai.dispatcher.repositories.ExecContextSegmentRepository;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.ai.utils.TxUtils;
import ai.metaheuristic.api.data.exec_context.ExecContextApiData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Variable-state entries on segments (041-EXEC-CONTEXT-SEGMENTS-PLAN, Phase 9): a Task's entry - its inputs and outputs
 * with their inited / nullified flags - lives in the segment of the Task's line, and each write touches only the segments
 * of the Tasks it names.
 *
 * <p>Entries are per Task. The whole-ExecContext record also copied flags ACROSS Tasks - an uploaded output marked the
 * matching input of every Task, and a new entry back-filled its inputs from every other entry - which on segments would
 * touch every segment under the producer's ctx. Those copies were display data: a Task's own inputs are set when its
 * variables are initialized ({@link #updateInputVariableStates}), and variable resolution reads {@code MH_VARIABLE}. The
 * readers that display entries derive the input flags at read time (Phase 12).
 *
 * <p>Callers hold the ExecContext's task-state lock: every writer of a segment runs under it, so two writes of one
 * segment never race on its {@code VERSION}.
 *
 * <p>Error code prefix: {@code 01.917.} (unique to this class).
 */
@Service
@Profile("dispatcher")
@Slf4j
@RequiredArgsConstructor(onConstructor_ = {@Autowired})
public class ExecContextSegmentVariableStateTxService {

    private final ExecContextSegmentRepository segmentRepository;
    private final ExecContextSegmentTxService segmentTxService;
    private final TaskRepository taskRepository;
    private final EventPublisherService eventPublisherService;

    /** Entries of created Tasks: an existing entry of the Task has its inputs and outputs replaced, else it is added. */
    @Transactional
    public void registerCreatedTasks(Long execContextId, List<ExecContextApiData.VariableState> entries) {
        TxUtils.checkTxExists();
        final Touched touched = new Touched(execContextId);
        for (ExecContextApiData.VariableState e : entries) {
            final ExecContextSegment s = touched.segmentOf(e.taskId, e.taskContextId);
            if (s == null) {
                continue;
            }
            final List<ExecContextApiData.VariableState> states = s.getExecContextSegmentParams().variableStates;
            final ExecContextApiData.VariableState existing = find(states, e.taskId);
            if (existing == null) {
                states.add(e);
            }
            else {
                existing.inputs = e.inputs;
                existing.outputs = e.outputs;
            }
        }
        touched.save();
    }

    /** An uploaded output: the producing Task's entry marks it inited / nullified; the Task is checked for completion. */
    @Transactional
    public void registerVariableStates(Long execContextId, List<VariableUploadedEvent> events) {
        TxUtils.checkTxExists();
        final Touched touched = new Touched(execContextId);
        final Set<Long> tasksToCheck = new LinkedHashSet<>();
        for (VariableUploadedEvent event : events) {
            tasksToCheck.add(event.taskId);
            final ExecContextSegment s = touched.segmentOf(event.taskId, null);
            final ExecContextApiData.VariableState state = s == null ? null : find(s.getExecContextSegmentParams().variableStates, event.taskId);
            if (state == null || state.outputs == null || state.outputs.isEmpty()) {
                log.warn("01.917.020 no output entry of Task #{} to mark, event {}", event.taskId, event);
                continue;
            }
            for (ExecContextApiData.VariableInfo output : state.outputs) {
                if (output.id.equals(event.variableId)) {
                    output.inited = true;
                    output.nullified = event.nullified;
                }
            }
        }
        touched.save();
        tasksToCheck.forEach(taskId -> eventPublisherService.publishCheckTaskCanBeFinishedTxEvent(new CheckTaskCanBeFinishedTxEvent(execContextId, taskId)));
    }

    /** A Task's input variables were initialized: its entry's inputs take their inited / nullified flags. */
    @Transactional
    public void updateInputVariableStates(Long execContextId, List<InputVariablesInitedEvent> events) {
        TxUtils.checkTxExists();
        final Touched touched = new Touched(execContextId);
        for (InputVariablesInitedEvent event : events) {
            final ExecContextSegment s = touched.segmentOf(event.taskId, null);
            final ExecContextApiData.VariableState state = s == null ? null : find(s.getExecContextSegmentParams().variableStates, event.taskId);
            if (state == null || state.inputs == null) {
                continue;
            }
            for (ExecContextApiData.VariableInfo input : state.inputs) {
                for (InputVariablesInitedEvent.InputVariableState ivs : event.inputStates) {
                    if (input.id.equals(ivs.variableId)) {
                        input.inited = true;
                        input.nullified = ivs.nullified;
                    }
                }
            }
        }
        touched.save();
    }

    private static ExecContextApiData.@Nullable VariableState find(List<ExecContextApiData.VariableState> states, Long taskId) {
        for (ExecContextApiData.VariableState s : states) {
            if (taskId.equals(s.taskId)) {
                return s;
            }
        }
        return null;
    }

    /** The segments one call reads, by ctx, and which of them it changed. */
    private final class Touched {
        private final Long execContextId;
        private final Map<String, ExecContextSegment> byCtx = new HashMap<>();
        private final Map<Long, ExecContextSegment> changed = new LinkedHashMap<>();

        private Touched(Long execContextId) {
            this.execContextId = execContextId;
        }

        /** The segment of a Task's line (ctx given, or read from the Task); null - logged - when there is none. */
        @Nullable
        ExecContextSegment segmentOf(Long taskId, @Nullable String ctxOrNull) {
            String ctx = ctxOrNull;
            if (ctx == null) {
                final TaskImpl t = taskRepository.findByIdReadOnly(taskId);
                if (t == null) {
                    log.warn("01.917.040 Task #{} not found, ExecContext #{}", taskId, execContextId);
                    return null;
                }
                ctx = t.getTaskParamsYaml().task.taskContextId;
            }
            final String c = ctx;
            final ExecContextSegment s = byCtx.computeIfAbsent(c, k -> segmentTxService.findSegmentOfCtx(execContextId, k));
            if (s == null) {
                log.warn("01.917.060 no segment owns ctx {} of Task #{}, ExecContext #{}", c, taskId, execContextId);
                return null;
            }
            changed.put(s.id, s);
            return s;
        }

        /** Writes every segment a call reached; entries are not structure, so STRUCTURE_HASH stays as it is. */
        void save() {
            for (ExecContextSegment s : changed.values()) {
                s.updateParams(s.getExecContextSegmentParams());
                segmentRepository.save(s);
            }
        }
    }
}
