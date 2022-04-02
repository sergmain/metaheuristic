/*
 * Metaheuristic, Copyright (C) 2017-2021, Innovation platforms, LLC
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

package ai.metaheuristic.commons.yaml.env;

import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.exceptions.BlankYamlParamsException;
import ai.metaheuristic.commons.yaml.YamlUtils;
import ai.metaheuristic.commons.yaml.versioning.AbstractParamsYamlUtils;
import org.springframework.lang.NonNull;
import org.yaml.snakeyaml.Yaml;

import java.util.stream.Collectors;

/**
 * @author Serge
 * Date: 04/01/2022
 * Time: 5:15 PM
 */
public class EnvParamsYamlUtilsV4
        extends AbstractParamsYamlUtils<EnvParamsYamlV4, EnvParamsYaml, Void, Void, Void, Void> {

    @Override
    public int getVersion() {
        return 4;
    }

    @NonNull
    @Override
    public Yaml getYaml() {
        return YamlUtils.init(EnvParamsYamlV4.class);
    }

    @NonNull
    @Override
    public EnvParamsYaml upgradeTo(@NonNull EnvParamsYamlV4 src) {
        src.checkIntegrity();
        EnvParamsYaml trg = new EnvParamsYaml();

        trg.mirrors.putAll(src.mirrors);
        src.envs.stream().map(EnvParamsYamlUtilsV4::to).collect(Collectors.toCollection(() -> trg.envs));
        src.disk.stream().map(o->new EnvParamsYaml.DiskStorage(o.code, o.path)).collect(Collectors.toCollection(() -> trg.disk));
        src.processors.stream().map(o->new EnvParamsYaml.Processor(o.code, o.tags)).collect(Collectors.toCollection(()->trg.processors));
        src.quotas.values.stream().map(o->new EnvParamsYaml.Quota(o.tag, o.amount, o.processingTime)).collect(Collectors.toCollection(()->trg.quotas.values));
        trg.quotas.limit = src.quotas.limit;
        trg.quotas.disabled = src.quotas.disabled;
        trg.quotas.defaultValue = src.quotas.defaultValue;

        trg.checkIntegrity();
        return trg;
    }

    private static EnvParamsYaml.Env to(EnvParamsYamlV4.EnvV4 v4) {
        EnvParamsYaml.Env env = new EnvParamsYaml.Env();
        env.code = v4.code;
        env.exec = v4.exec;
        if (v4.verify!=null) {
            EnvParamsYaml.VerifyEnv verifyEnv = new EnvParamsYaml.VerifyEnv();
            verifyEnv.run = v4.verify.run;
            verifyEnv.params = v4.verify.params;
            verifyEnv.responsePattern = v4.verify.responsePattern;
            verifyEnv.expectedResponse = v4.verify.expectedResponse;
            env.verify = verifyEnv;
        }
        return env;
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
    public String toString(@NonNull EnvParamsYamlV4 yaml) {
        return getYaml().dump(yaml);
    }

    @NonNull
    @Override
    public EnvParamsYamlV4 to(@NonNull String yaml) {
        if (S.b(yaml)) {
            throw new BlankYamlParamsException("'yaml' parameter is blank");
        }
        final EnvParamsYamlV4 p = getYaml().load(yaml);
        return p;
    }

}
