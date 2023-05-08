/*
 *    Copyright 2023, Sergio Lissner, Innovation platforms, LLC
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 *
 */

package ai.metaheuristic.ai.yaml.kb;

import ai.metaheuristic.commons.yaml.YamlUtils;
import ai.metaheuristic.commons.yaml.versioning.AbstractParamsYamlUtils;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.yaml.snakeyaml.Yaml;

import java.util.List;
import java.util.stream.Collectors;

public class KbParamsUtilsV1 extends
        AbstractParamsYamlUtils<KbParamsV1, KbParams, Void, Void, Void, Void> {

    @Override
    public int getVersion() {
        return 1;
    }

    @NonNull
    @Override
    public Yaml getYaml() {
        return YamlUtils.init(KbParamsV1.class);
    }

    @NonNull
    @Override
    public KbParams upgradeTo(@NonNull KbParamsV1 v1) {
        v1.checkIntegrity();

        KbParams t = new KbParams();
        t.disabled = v1.disabled;
        t.kb.code = v1.kb.code;
        t.kb.type = v1.kb.type;
        t.kb.git = toGit(v1.kb.git);
        t.kb.file = toFile(v1.kb.file);
        t.kb.inline = toInline(v1.kb.inline);

        t.checkIntegrity();
        return t;
    }

    @Nullable
    private static KbParams.File toFile(@Nullable KbParamsV1.FileV1 v1) {
        if (v1==null) {
            return null;
        }
        KbParams.File f = new KbParams.File(v1.url);
        return f;
    }

    @Nullable
    private static KbParams.Git toGit(@Nullable KbParamsV1.GitV1 v1) {
        if (v1==null) {
            return null;
        }
        KbParams.Git g = new KbParams.Git(v1.repo, v1.branch, v1.commit);
        v1.kbPaths.stream().map(KbParamsUtilsV1::toKbPath).collect(Collectors.toCollection(()->g.kbPaths));
        return g;
    }

    @Nullable
    public static KbParams.KbPath toKbPath(KbParamsV1.KbPathV1 v1) {
        KbParams.KbPath ta = new KbParams.KbPath(v1.evals, v1.data);
        return ta;
    }

    @Nullable
    public static List<KbParams.Inline> toInline(@Nullable List<KbParamsV1.InlineV1> v1) {
        if (v1==null) {
            return null;
        }
        return v1.stream().map(o->new KbParams.Inline(o.p, o.a)).toList();
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
    public String toString(@NonNull KbParamsV1 yaml) {
        yaml.checkIntegrity();

        return getYaml().dump(yaml);
    }

    @NonNull
    @Override
    public KbParamsV1 to(@NonNull String s) {
        final KbParamsV1 p = getYaml().load(s);
        return p;
    }

}
