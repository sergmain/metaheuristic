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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * The live {@link GraftExpander}: routes an in-band graft node to the graft service's in-band entry points
 * (which invoke graftTxService directly, joining the caller's tx under its held Graph+TaskState locks). Its
 * only dependency is {@link ExecContextGraftService}, which is not downstream of the sub-process drivers, so
 * injecting THIS bean into the drivers is cycle-free (025 Phase 6.3b). Driver semantics (025 v1):
 * place-now -> a dormant SKIPPED sibling line (reopenable by objection); run-now -> a live (PRE_INIT) line
 * that runs with the pipeline (no reset - the EC is still producing in-band, unlike the out-of-band case).
 */
@Service
@Profile("dispatcher")
@Slf4j
@RequiredArgsConstructor
public class InBandGraftExpander implements GraftExpander {

    private final ExecContextGraftService execContextGraftService;

    @Override
    public List<Long> expand(Long execContextId, ExecContextParamsYaml.Process graftNode, Long targetTaskId, String currTaskContextId) {
        final ExecContextParamsYaml.Graft graft = graftNode.graft;
        if (graft == null) {
            throw new IllegalStateException("832.020 expand() called on a non-graft process " + graftNode.processCode);
        }
        // 032 - materialize the authored bind() inputs write-once at the grafted line ctx (positional
        // map onto the group's declared formals, value read at the target ctx). Enables a rebind such as
        // the per-level depth counter (enclosing nextDepth -> child formal depth).
        final List<ExecContextGraftService.InputBinding> inputs =
                execContextGraftService.resolveInBandInputBindings(execContextId, currTaskContextId, graft);
        final String driver = graft.driver == null ? "place-now" : graft.driver;
        return switch (driver) {
            // run-now: a live line - if it could not terminate at graft time, its unwired tail(s) rejoin
            // the enclosing block's downstream via the caller's lastIds.
            case "run-now" -> execContextGraftService.attachGroupInBandRunNow(
                    execContextId, targetTaskId, graft.groupName, inputs).unwiredTails();
            // place-now: a SKIPPED (dormant, objection-reopenable) line - it does NOT rejoin the flow.
            case "place-now" -> {
                execContextGraftService.attachGroupInBandPlaceNow(execContextId, targetTaskId, graft.groupName, inputs);
                yield List.of();
            }
            default -> throw new IllegalStateException(
                    "832.040 unknown graft driver '" + driver + "' on " + graftNode.processCode);
        };
    }
}
