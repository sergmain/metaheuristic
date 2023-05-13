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

package ai.metaheuristic.ai.mhbp.yaml.chapter;

import ai.metaheuristic.commons.yaml.YamlUtils;
import ai.metaheuristic.commons.yaml.versioning.AbstractParamsYamlUtils;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.yaml.snakeyaml.Yaml;

import java.util.stream.Collectors;

public class ChapterParamsUtilsV1 extends
        AbstractParamsYamlUtils<ChapterParamsV1, ChapterParams, Void, Void, Void, Void> {

    @Override
    public int getVersion() {
        return 1;
    }

    @NonNull
    @Override
    public Yaml getYaml() {
        return YamlUtils.init(ChapterParamsV1.class);
    }

    @NonNull
    @Override
    public ChapterParams upgradeTo(@NonNull ChapterParamsV1 v1) {
        v1.checkIntegrity();

        ChapterParams t = new ChapterParams();
        t.prompts = v1.prompts.stream().map(ChapterParamsUtilsV1::toPrompt).collect(Collectors.toList());
        t.checkIntegrity();
        return t;
    }

    @Nullable
    private static ChapterParams.Prompt toPrompt(ChapterParamsV1.PromptV1 v1) {
        ChapterParams.Prompt f = new ChapterParams.Prompt(v1.p, v1.a);
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
    public String toString(@NonNull ChapterParamsV1 yaml) {
        yaml.checkIntegrity();

        return getYaml().dump(yaml);
    }

    @NonNull
    @Override
    public ChapterParamsV1 to(@NonNull String s) {
        final ChapterParamsV1 p = getYaml().load(s);
        return p;
    }

}