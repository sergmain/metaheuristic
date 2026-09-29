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

package ai.metaheuristic.ai.yaml.exec_context_graph;

import ai.metaheuristic.commons.exceptions.DowngradeNotSupportedException;
import ai.metaheuristic.commons.exceptions.ParamsProcessingException;
import ai.metaheuristic.commons.json.versioning_json.AbstractParamsJsonUtils;
import ai.metaheuristic.commons.json.versioning_json.BaseJsonUtils;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.JacksonException;

import org.jspecify.annotations.NonNull;

/**
 * V1 of the ExecContextGraphParams JSON chain, and currently its head: it upgrades V1 straight to the version-less
 * {@link ExecContextGraphParams}, so {@link #nextUtil()} ends the chain.
 *
 * <p>Error code prefix: {@code 01.902.} (unique to this class).
 *
 * @author Serge
 * Date: 3/17/2021
 * Time: 10:47 AM
 */
public class ExecContextGraphParamsUtilsV1
        extends AbstractParamsJsonUtils<
    ExecContextGraphParamsV1, ExecContextGraphParams, Void,
        Void, Void, Void> {

    @Override
    public int getVersion() {
        return 1;
    }

    @Override
    public ExecContextGraphParams upgradeTo(ExecContextGraphParamsV1 v1) {
        ExecContextGraphParams t = new ExecContextGraphParams();
        t.graph = v1.graph;
        return t;
    }

    @Override
    public Void downgradeTo(Void unused) {
        throw new DowngradeNotSupportedException();
    }

    @Override
    public @Nullable Void nextUtil() {
        return null;
    }

    @Override
    public @Nullable Void prevUtil() {
        return null;
    }

    @Override
    public String toString(ExecContextGraphParamsV1 json) {
        try {
            return BaseJsonUtils.getMapper().writeValueAsString(json);
        }
        catch (JacksonException e) {
            throw new ParamsProcessingException("01.902.040 Error writing ExecContextGraphParamsV1: " + e.getMessage(), e);
        }
    }

    @Override
    public ExecContextGraphParamsV1 to(String s) {
        try {
            return BaseJsonUtils.getMapper().readValue(s, ExecContextGraphParamsV1.class);
        }
        catch (JacksonException e) {
            throw new ParamsProcessingException("01.902.020 Error reading ExecContextGraphParamsV1: " + e.getMessage(), e);
        }
    }
}
