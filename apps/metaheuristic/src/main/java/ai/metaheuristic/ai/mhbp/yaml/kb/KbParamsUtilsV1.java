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

package ai.metaheuristic.ai.mhbp.yaml.kb;

import ai.metaheuristic.commons.yaml.YamlUtils;
import ai.metaheuristic.commons.yaml.versioning.AbstractParamsYamlUtils;
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

    @Override
    public Yaml getYaml() {
        return YamlUtils.init(KbParamsV1.class);
    }

    @Override
    public KbParams upgradeTo(KbParamsV1 v1) {
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

    @Override
    public Void downgradeTo(Void yaml) {
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
    public String toString(KbParamsV1 yaml) {
        yaml.checkIntegrity();

        return getYaml().dump(yaml);
    }

    @Override
    public KbParamsV1 to(String s) {
        final KbParamsV1 p = getYaml().load(s);
        return p;
    }

}
