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

package ai.metaheuristic.ai.dispatcher.exec_context_graph;

import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;

import java.util.List;

/**
 * Threaded into {@code TaskProducingService.createTasksForSubProcesses} as a FUNCTION PARAMETER so the
 * task-production loop can expand an in-band graft node (a {@link ExecContextParamsYaml.Process} whose
 * {@code graft} tag is non-null) WITHOUT a field dependency on the graft service - that field dep would
 * make a Spring ctor cycle (GraftService -> GraftTxService -> TaskProducingService -> GraftService). The
 * real implementation is {@link InBandGraftExpander}; the recursion caller passes a fail-fast lambda (025 v1).
 */
@FunctionalInterface
public interface GraftExpander {
    /** Expand the in-band graft node under {@code targetTaskId}; return the grafted line's head task id. */
    // returns the grafted line's UNWIRED tail task ids (to be rejoined into the enclosing block's
    // downstream by the caller); empty when the line self-terminates or is a dormant SKIPPED (place-now) line.
    List<Long> expand(Long execContextId, ExecContextParamsYaml.Process graftNode, Long targetTaskId);
}
