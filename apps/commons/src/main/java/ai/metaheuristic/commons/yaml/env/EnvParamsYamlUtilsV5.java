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

package ai.metaheuristic.commons.yaml.env;

import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.exceptions.BlankYamlParamsException;
import ai.metaheuristic.commons.yaml.YamlUtils;
import ai.metaheuristic.commons.yaml.versioning.AbstractParamsYamlUtils;
import javax.annotation.Nonnull;
import org.yaml.snakeyaml.Yaml;

import java.util.stream.Collectors;

/**
 * @author Serge
 * Date: 04/01/2022
 * Time: 5:15 PM
 */
public class EnvParamsYamlUtilsV5
        extends AbstractParamsYamlUtils<EnvParamsYamlV5, EnvParamsYaml, Void, Void, Void, Void> {

    @Override
    public int getVersion() {
        return 5;
    }

    @Nonnull
    @Override
    public Yaml getYaml() {
        return YamlUtils.init(EnvParamsYamlV5.class);
    }

    @Nonnull
    @Override
    public EnvParamsYaml upgradeTo(@Nonnull EnvParamsYamlV5 src) {
        src.checkIntegrity();
        EnvParamsYaml trg = new EnvParamsYaml();

        trg.mirrors.putAll(src.mirrors);
        src.envs.stream().map(EnvParamsYamlUtilsV5::to).collect(Collectors.toCollection(() -> trg.envs));
        src.disk.stream().map(o->new EnvParamsYaml.DiskStorage(o.code, o.path)).collect(Collectors.toCollection(() -> trg.disk));
        src.cores.stream().map(o->new EnvParamsYaml.Core(o.code, o.tags)).collect(Collectors.toCollection(()->trg.cores));
        src.quotas.values.stream().map(o->new EnvParamsYaml.Quota(o.tag, o.amount, o.processingTime)).collect(Collectors.toCollection(()->trg.quotas.values));
        trg.quotas.limit = src.quotas.limit;
        trg.quotas.disabled = src.quotas.disabled;
        trg.quotas.defaultValue = src.quotas.defaultValue;

        trg.checkIntegrity();
        return trg;
    }

    private static EnvParamsYaml.Env to(EnvParamsYamlV5.EnvV5 V5) {
        EnvParamsYaml.Env env = new EnvParamsYaml.Env();
        env.code = V5.code;
        env.exec = V5.exec;
        if (V5.verify!=null) {
            EnvParamsYaml.VerifyEnv verifyEnv = new EnvParamsYaml.VerifyEnv();
            verifyEnv.run = V5.verify.run;
            verifyEnv.params = V5.verify.params;
            verifyEnv.responsePattern = V5.verify.responsePattern;
            verifyEnv.expectedResponse = V5.verify.expectedResponse;
            env.verify = verifyEnv;
        }
        return env;
    }

    @Nonnull
    @Override
    public Void downgradeTo(@Nonnull Void yaml) {
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
    public String toString(@Nonnull EnvParamsYamlV5 yaml) {
        return getYaml().dump(yaml);
    }

    @Nonnull
    @Override
    public EnvParamsYamlV5 to(@Nonnull String yaml) {
        if (S.b(yaml)) {
            throw new BlankYamlParamsException("'yaml' parameter is blank");
        }
        final EnvParamsYamlV5 p = getYaml().load(yaml);
        return p;
    }

}
