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

package ai.metaheuristic.ai.dispatcher.execution_gate;

import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.dispatcher.beans.ExecutionGate;
import ai.metaheuristic.ai.dispatcher.event.events.ExecutionGateChangedTxEvent;
import ai.metaheuristic.ai.dispatcher.repositories.ExecutionGateRepository;
import ai.metaheuristic.ai.yaml.execution_gate.ExecutionGateParamsYaml;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The writes behind the admission gate, and nothing else.
 *
 * <p>Every method here assumes its inputs were already decided by {@link ExecutionGateService}:
 * whether a live block already covers the key, and what deadline should result. That decision is a
 * context read and belongs outside the transaction. Loading the row about to be updated is not a
 * context read — it is the bean being written — which is why it happens here.
 *
 * <p>Error code prefix: {@code 01.321.} (unique to this class).
 *
 * @author Sergio Lissner
 * Date: 8/14/2026
 */
@Service
@Profile("dispatcher")
@Slf4j
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class ExecutionGateTxService {

    private final ExecutionGateRepository executionGateRepository;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Creates the row for a key, or pushes an existing row's deadline out to the later of the two.
     *
     * <p>The unique index on (SCOPE, REF_KEY) is the backstop against two callers inserting at once,
     * not the primary mechanism — the normal path finds the row and extends it.
     */
    @Transactional
    public void createOrExtend(Enums.GateScope scope, String refKey, long blockedUntil, String reasonCode, ExecutionGateParamsYaml params) {
        ExecutionGate gate = executionGateRepository.findByScopeAndRefKey(scope.name(), refKey);
        if (gate == null) {
            gate = new ExecutionGate();
            gate.scope = scope.name();
            gate.refKey = refKey;
            gate.createdOn = System.currentTimeMillis();
            gate.blockedUntil = blockedUntil;
        }
        else {
            // a shorter block must never shorten a longer one already in force
            gate.blockedUntil = Math.max(gate.blockedUntil, blockedUntil);
        }
        gate.reasonCode = reasonCode;
        gate.updateParams(params);

        final ExecutionGate saved = executionGateRepository.save(gate);
        log.info("01.321.020 execution gate committed, scope: {}, refKey: {}, blockedUntil: {}, reason: {}",
                scope, refKey, saved.blockedUntil, reasonCode);

        eventPublisher.publishEvent(new ExecutionGateChangedTxEvent(scope, refKey, saved.blockedUntil, reasonCode, false));
    }

    /**
     * Removes the row for a key. An expired block is deleted rather than kept as history: adding
     * history later is easy, removing it once the table is a performance problem is not.
     */
    @Transactional
    public void delete(Enums.GateScope scope, String refKey) {
        final ExecutionGate gate = executionGateRepository.findByScopeAndRefKey(scope.name(), refKey);
        if (gate == null) {
            return;
        }
        final String reasonCode = gate.reasonCode;
        executionGateRepository.delete(gate);
        log.info("01.321.040 execution gate released, scope: {}, refKey: {}", scope, refKey);

        eventPublisher.publishEvent(new ExecutionGateChangedTxEvent(scope, refKey, 0L, reasonCode, true));
    }
}
