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

package ai.metaheuristic.ai.dispatcher.rest.v1;

import ai.metaheuristic.ai.dispatcher.data.ExecutionGateViewData;
import ai.metaheuristic.ai.dispatcher.execution_gate.ExecutionGateService;
import ai.metaheuristic.ai.dispatcher.monitoring.GateMonitoring;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * The admin view of what is currently being withheld, and why.
 *
 * <p>Admin-scoped like the Processor views it sits beside: a block names a Function code, an API key
 * code or a Processor, and the rejection exemplars carry offending values out of Task configuration.
 * None of that is for an ordinary user of the installation.
 *
 * @author Sergio Lissner
 * Date: 8/14/2026
 */
@RestController
@RequestMapping("/rest/v1/dispatcher")
@Profile("dispatcher")
@CrossOrigin
@PreAuthorize("hasAnyRole('ADMIN')")
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class ExecutionGateRestController {

    private final ExecutionGateService executionGateService;
    private final GateMonitoring gateMonitoring;

    /** Everything the gates screen needs, in one round trip — the two halves are always read together. */
    @GetMapping("/execution-gate")
    public ExecutionGateViewData.GateStatus gateStatus() {
        final long now = System.currentTimeMillis();
        return new ExecutionGateViewData.GateStatus(
                executionGateService.liveRecords().stream()
                        .map(r -> new ExecutionGateViewData.GateRecordView(
                                r.scope(), r.refKey(), r.reasonCode(), r.blockedUntil(), r.blockedUntil() - now))
                        .toList(),
                gateMonitoring.actionableView(now).stream()
                        .map(level -> new ExecutionGateViewData.ReasonLevelView(
                                level.reason().name(),
                                level.rejectionClass().name(),
                                level.count(),
                                level.bucketsPresent(),
                                level.exemplars().stream()
                                        .map(e -> new ExecutionGateViewData.ExemplarView(
                                                e.atMills(), e.taskId(), e.functionCode(), e.processorId(), e.offendingValue()))
                                        .toList()))
                        .toList());
    }
}
