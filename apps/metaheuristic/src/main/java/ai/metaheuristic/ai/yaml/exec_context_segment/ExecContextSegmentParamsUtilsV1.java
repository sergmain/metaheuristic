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

package ai.metaheuristic.ai.yaml.exec_context_segment;

import ai.metaheuristic.commons.exceptions.DowngradeNotSupportedException;
import ai.metaheuristic.commons.exceptions.ParamsProcessingException;
import ai.metaheuristic.commons.json.versioning_json.AbstractParamsJsonUtils;
import ai.metaheuristic.commons.json.versioning_json.BaseJsonUtils;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.JacksonException;

import org.jspecify.annotations.NonNull;

/**
 * V1 of the ExecContextSegmentParams JSON chain, and currently its head: it upgrades V1 straight to the version-less
 * {@link ExecContextSegmentParams}, so {@link #nextUtil()} ends the chain.
 *
 * <p>Error code prefix: {@code 01.905.} (unique to this class).
 */
public class ExecContextSegmentParamsUtilsV1
        extends AbstractParamsJsonUtils<
        ExecContextSegmentParamsV1, ExecContextSegmentParams, Void,
        Void, Void, Void> {

    @Override
    public int getVersion() {
        return 1;
    }

    @Override
    public ExecContextSegmentParams upgradeTo(ExecContextSegmentParamsV1 v1) {
        ExecContextSegmentParams t = new ExecContextSegmentParams();
        for (ExecContextSegmentParamsV1.LineV1 lineV1 : v1.lines) {
            ExecContextSegmentParams.Line line = new ExecContextSegmentParams.Line(lineV1.ctx, lineV1.forkTaskId);
            line.registered = lineV1.registered;
            for (ExecContextSegmentParamsV1.VertexV1 vertexV1 : lineV1.tasks) {
                line.tasks.add(new ExecContextSegmentParams.Vertex(vertexV1.taskId, vertexV1.tag));
            }
            t.lines.add(line);
        }
        t.states.putAll(v1.states);
        t.triesWasMade.putAll(v1.triesWasMade);
        t.variableStates.addAll(v1.variableStates);
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
    public String toString(ExecContextSegmentParamsV1 json) {
        try {
            return BaseJsonUtils.getMapper().writeValueAsString(json);
        }
        catch (JacksonException e) {
            throw new ParamsProcessingException("01.905.040 Error writing ExecContextSegmentParamsV1: " + e.getMessage(), e);
        }
    }

    @Override
    public ExecContextSegmentParamsV1 to(String s) {
        try {
            return BaseJsonUtils.getMapper().readValue(s, ExecContextSegmentParamsV1.class);
        }
        catch (JacksonException e) {
            throw new ParamsProcessingException("01.905.020 Error reading ExecContextSegmentParamsV1: " + e.getMessage(), e);
        }
    }
}
