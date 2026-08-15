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
import ai.metaheuristic.ai.yaml.execution_gate.ExecutionGateParamsYaml;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.OperationStatusRest;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.utils.FunctionAnalyzerUtils;
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

    /**
     * Blocks a key by hand.
     *
     * <p>❗ An operator needs a lever the automatic path does not provide: taking a machine out of
     * rotation before it does more damage, when nothing it printed happened to match an analyzer.
     * Without this the only way to withhold work is to wait for a Function to fail in a recognised
     * way, which is not a control at all.
     *
     * @param scope   one of the {@code EnumsApi.GateScope} constants
     * @param timeout as a Function descriptor writes it — {@code 30s}, {@code 20min}, {@code 1h}
     */
    @PostMapping("/execution-gate-block-commit")
    public OperationStatusRest block(String scope, String refKey, String timeout, String reasonCode) {
        if (S.b(scope) || S.b(refKey) || S.b(timeout)) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "01.327.020 scope, refKey and timeout are required");
        }
        final EnumsApi.GateScope gateScope;
        final long blockedUntil;
        try {
            gateScope = EnumsApi.GateScope.valueOf(scope.strip());
            blockedUntil = System.currentTimeMillis() + FunctionAnalyzerUtils.parseTimeout(timeout).toMillis();
        }
        catch (IllegalArgumentException | IllegalStateException e) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "01.327.040 " + e.getMessage());
        }
        final ExecutionGateParamsYaml params = new ExecutionGateParamsYaml();
        executionGateService.quarantine(gateScope, refKey.strip(), blockedUntil,
                S.b(reasonCode) ? "manual" : reasonCode.strip(), params);
        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    /**
     * Clears a block ahead of its deadline.
     *
     * <p>The other half of the lever, and the more important one: a rule that blocked too broadly or
     * for too long is otherwise unfixable until it expires, which for a day-long timeout means a day.
     */
    @PostMapping("/execution-gate-release-commit")
    public OperationStatusRest release(String scope, String refKey) {
        if (S.b(scope) || S.b(refKey)) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "01.327.060 scope and refKey are required");
        }
        try {
            executionGateService.release(EnumsApi.GateScope.valueOf(scope.strip()), refKey.strip());
        }
        catch (IllegalArgumentException e) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "01.327.080 unknown scope: " + scope);
        }
        return OperationStatusRest.OPERATION_STATUS_OK;
    }

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
