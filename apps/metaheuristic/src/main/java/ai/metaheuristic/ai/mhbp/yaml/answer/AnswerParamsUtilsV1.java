/*
 * Metaheuristic, Copyright (C) 2017-2023, Innovation platforms, LLC
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

package ai.metaheuristic.ai.mhbp.yaml.answer;

import ai.metaheuristic.commons.yaml.YamlUtils;
import ai.metaheuristic.commons.yaml.versioning.AbstractParamsYamlUtils;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.yaml.snakeyaml.Yaml;

import java.util.stream.Collectors;

public class AnswerParamsUtilsV1 extends
        AbstractParamsYamlUtils<AnswerParamsV1, AnswerParams, Void, Void, Void, Void> {

    @Override
    public int getVersion() {
        return 1;
    }

    @NonNull
    @Override
    public Yaml getYaml() {
        return YamlUtils.init(AnswerParamsV1.class);
    }

    @NonNull
    @Override
    public AnswerParams upgradeTo(@NonNull AnswerParamsV1 v1) {
        v1.checkIntegrity();

        AnswerParams t = new AnswerParams();
        t.results = v1.results.stream().map(AnswerParamsUtilsV1::toResult).collect(Collectors.toList());
        t.total = v1.total;
        t.processingMills = v1.processingMills;
        t.checkIntegrity();
        return t;
    }

    @Nullable
    private static AnswerParams.Result toResult(AnswerParamsV1.ResultV1 v1) {
        AnswerParams.Result f = new AnswerParams.Result(v1.p, v1.a, v1.e, v1.r, v1.s);
        return f;
    }

    @NonNull
    @Override
    public Void downgradeTo(@NonNull Void yaml) {
        return null;
    }

    @Override
    public Void nextUtil() {
        return null;
    }

    @Override
    public Void prevUtil() {
        return null;
    }

    @Override
    public String toString(@NonNull AnswerParamsV1 yaml) {
        yaml.checkIntegrity();

        return getYaml().dump(yaml);
    }

    @NonNull
    @Override
    public AnswerParamsV1 to(@NonNull String s) {
        final AnswerParamsV1 p = getYaml().load(s);
        return p;
    }

}
