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

package ai.metaheuristic.ai.dispatcher.beans;

import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.ai.yaml.execution_gate.ExecutionGateParamsYaml;
import ai.metaheuristic.ai.yaml.execution_gate.ExecutionGateParamsYamlUtils;
import ai.metaheuristic.commons.utils.threads.ThreadUtils;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.io.Serial;
import java.io.Serializable;

/**
 * One durable decision to withhold work from a scope until a deadline passes.
 *
 * <p>A row exists only while the block is live; an expired one is deleted rather than kept as
 * history. That is the cheaper mistake to correct — adding history later is easy, removing it after
 * the table has become a performance problem is not.
 *
 * <p>{@code SCOPE} + {@code REF_KEY} carry a unique index, and that index is the whole idempotency
 * mechanism: blocking the same key twice extends {@code BLOCKED_UNTIL} on the row that is already
 * there, it never stacks a second one.
 *
 * <p>{@code REF_KEY} is interpreted per scope — a Function code, a company id joined to an API key
 * code, or a processor id rendered as a string. It is a string rather than a typed column precisely
 * so one table serves every scope: a second home would fork the unique index, the expiry sweep, the
 * startup load and the deadline read, all four.
 *
 * @author Sergio Lissner
 * Date: 8/14/2026
 */
@Entity
@Table(name = "MH_EXECUTION_GATE")
@Data
@NoArgsConstructor
@ToString
public class ExecutionGate implements Serializable {

    @Serial
    private static final long serialVersionUID = -1524169204428471935L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Version
    public Integer version;

    /** Name of an {@code EnumsApi.GateScope} constant. */
    @Column(name = "SCOPE", nullable = false)
    public String scope;

    /** Scope-dependent identity of what is blocked. */
    @Column(name = "REF_KEY", nullable = false)
    public String refKey;

    /** Epoch millis. The block is live while now() is below this. */
    @Column(name = "BLOCKED_UNTIL", nullable = false)
    public long blockedUntil;

    @Column(name = "CREATED_ON", nullable = false)
    public long createdOn;

    /** Why the block was opened; free text supplied by whatever opened it. */
    @Column(name = "REASON_CODE", nullable = false)
    public String reasonCode;

    @Column(name = "PARAMS", nullable = false)
    private String params;

    public String getParams() {
        return params;
    }

    public void setParams(String params) {
        this.paramsLocked.reset(() -> this.params = params);
    }

    @Transient
    @JsonIgnore
    private final ThreadUtils.CommonThreadLocker<ExecutionGateParamsYaml> paramsLocked =
            new ThreadUtils.CommonThreadLocker<>(this::parseParams);

    private ExecutionGateParamsYaml parseParams() {
        ExecutionGateParamsYaml temp = ExecutionGateParamsYamlUtils.BASE_YAML_UTILS.to(params);
        return temp == null ? new ExecutionGateParamsYaml() : temp;
    }

    @JsonIgnore
    public ExecutionGateParamsYaml getExecutionGateParamsYaml() {
        return paramsLocked.get();
    }

    @JsonIgnore
    public void updateParams(ExecutionGateParamsYaml egpy) {
        setParams(ExecutionGateParamsYamlUtils.BASE_YAML_UTILS.toString(egpy));
    }
}
