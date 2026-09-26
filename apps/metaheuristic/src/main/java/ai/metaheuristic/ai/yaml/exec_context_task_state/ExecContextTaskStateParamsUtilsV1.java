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

package ai.metaheuristic.ai.yaml.exec_context_task_state;

import ai.metaheuristic.commons.exceptions.DowngradeNotSupportedException;
import ai.metaheuristic.commons.exceptions.ParamsProcessingException;
import ai.metaheuristic.commons.json.versioning_json.AbstractParamsJsonUtils;
import ai.metaheuristic.commons.json.versioning_json.BaseJsonUtils;
import tools.jackson.core.JacksonException;

import org.jspecify.annotations.NonNull;

/**
 * V1 of the ExecContextTaskStateParams JSON chain, and currently its head: it upgrades V1 straight to the version-less
 * {@link ExecContextTaskStateParams}, so {@link #nextUtil()} ends the chain.
 *
 * <p>Error code prefix: {@code 01.903.} (unique to this class).
 *
 * @author Serge
 * Date: 3/17/2021
 * Time: 10:47 AM
 */
public class ExecContextTaskStateParamsUtilsV1
        extends AbstractParamsJsonUtils<
    ExecContextTaskStateParamsV1, ExecContextTaskStateParams, Void,
        Void, Void, Void> {

    @Override
    public int getVersion() {
        return 1;
    }

    @NonNull
    @Override
    public ExecContextTaskStateParams upgradeTo(@NonNull ExecContextTaskStateParamsV1 v1) {
        ExecContextTaskStateParams t = new ExecContextTaskStateParams();
        t.states.putAll(v1.states);
        t.triesWasMade.putAll(v1.triesWasMade);
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
    public String toString(@NonNull ExecContextTaskStateParamsV1 json) {
        try {
            return BaseJsonUtils.getMapper().writeValueAsString(json);
        }
        catch (JacksonException e) {
            throw new ParamsProcessingException("01.903.040 Error writing ExecContextTaskStateParamsV1: " + e.getMessage(), e);
        }
    }

    @NonNull
    @Override
    public ExecContextTaskStateParamsV1 to(@NonNull String s) {
        try {
            return BaseJsonUtils.getMapper().readValue(s, ExecContextTaskStateParamsV1.class);
        }
        catch (JacksonException e) {
            throw new ParamsProcessingException("01.903.020 Error reading ExecContextTaskStateParamsV1: " + e.getMessage(), e);
        }
    }
}
