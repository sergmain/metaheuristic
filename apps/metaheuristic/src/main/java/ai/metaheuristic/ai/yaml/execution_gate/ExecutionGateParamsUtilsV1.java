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

package ai.metaheuristic.ai.yaml.execution_gate;

import ai.metaheuristic.commons.exceptions.DowngradeNotSupportedException;
import ai.metaheuristic.commons.exceptions.ParamsProcessingException;
import ai.metaheuristic.commons.json.versioning_json.AbstractParamsJsonUtils;
import ai.metaheuristic.commons.json.versioning_json.BaseJsonUtils;
import tools.jackson.core.JacksonException;

import org.jspecify.annotations.NonNull;

/**
 * V1 of the ExecutionGateParams JSON chain, and currently its head: it upgrades V1 straight to the version-less
 * {@link ExecutionGateParams}, so {@link #nextUtil()} ends the chain.
 *
 * <p>Error code prefix: {@code 01.904.} (unique to this class).
 *
 * @author Sergio Lissner
 * Date: 8/14/2026
 */
public class ExecutionGateParamsUtilsV1
        extends AbstractParamsJsonUtils<
    ExecutionGateParamsV1, ExecutionGateParams, Void,
        Void, Void, Void> {

    @Override
    public int getVersion() {
        return 1;
    }

    @NonNull
    @Override
    public ExecutionGateParams upgradeTo(@NonNull ExecutionGateParamsV1 v1) {
        ExecutionGateParams t = new ExecutionGateParams();
        t.triggeredByTaskId = v1.triggeredByTaskId;
        t.functionCode = v1.functionCode;
        t.processorId = v1.processorId;
        t.matchedPattern = v1.matchedPattern;
        t.consoleExcerpt = v1.consoleExcerpt;
        t.incrementTries = v1.incrementTries;
        return t;
    }

    @Override
    public Void downgradeTo(@NonNull Void unused) {
        throw new DowngradeNotSupportedException();
    }

    @Override
    public Void nextUtil() {
        return null;
    }

    @Override
    public Void prevUtil() {
        return null;
    }

    @NonNull
    @Override
    public String toString(@NonNull ExecutionGateParamsV1 json) {
        try {
            return BaseJsonUtils.getMapper().writeValueAsString(json);
        }
        catch (JacksonException e) {
            throw new ParamsProcessingException("01.904.040 Error writing ExecutionGateParamsV1: " + e.getMessage(), e);
        }
    }

    @NonNull
    @Override
    public ExecutionGateParamsV1 to(@NonNull String s) {
        try {
            return BaseJsonUtils.getMapper().readValue(s, ExecutionGateParamsV1.class);
        }
        catch (JacksonException e) {
            throw new ParamsProcessingException("01.904.020 Error reading ExecutionGateParamsV1: " + e.getMessage(), e);
        }
    }
}
