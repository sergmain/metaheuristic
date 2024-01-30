/*
 * Metaheuristic, Copyright (C) 2017-2024, Innovation platforms, LLC
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
public class EnvParamsYamlUtilsV4
        extends AbstractParamsYamlUtils<EnvParamsYamlV4, EnvParamsYamlV5, EnvParamsYamlUtilsV5, Void, Void, Void> {

    @Override
    public int getVersion() {
        return 4;
    }

    @Nonnull
    @Override
    public Yaml getYaml() {
        return YamlUtils.init(EnvParamsYamlV4.class);
    }

    @Nonnull
    @Override
    public EnvParamsYamlV5 upgradeTo(@Nonnull EnvParamsYamlV4 src) {
        src.checkIntegrity();
        EnvParamsYamlV5 trg = new EnvParamsYamlV5();

        trg.mirrors.putAll(src.mirrors);
        src.envs.stream().map(EnvParamsYamlUtilsV4::to).collect(Collectors.toCollection(() -> trg.envs));
        src.disk.stream().map(o->new EnvParamsYamlV5.DiskStorageV5(o.code, o.path)).collect(Collectors.toCollection(() -> trg.disk));
        src.processors.stream().map(o->new EnvParamsYamlV5.CoreV5(o.code, o.tags)).collect(Collectors.toCollection(()->trg.cores));
        src.quotas.values.stream().map(o->new EnvParamsYamlV5.QuotaV5(o.tag, o.amount, o.processingTime)).collect(Collectors.toCollection(()->trg.quotas.values));
        trg.quotas.limit = src.quotas.limit;
        trg.quotas.disabled = src.quotas.disabled;
        trg.quotas.defaultValue = src.quotas.defaultValue;

        trg.checkIntegrity();
        return trg;
    }

    private static EnvParamsYamlV5.EnvV5 to(EnvParamsYamlV4.EnvV4 v4) {
        EnvParamsYamlV5.EnvV5 env = new EnvParamsYamlV5.EnvV5();
        env.code = v4.code;
        env.exec = v4.exec;
        if (v4.verify!=null) {
            EnvParamsYamlV5.VerifyEnvV5 verifyEnv = new EnvParamsYamlV5.VerifyEnvV5();
            verifyEnv.run = v4.verify.run;
            verifyEnv.params = v4.verify.params;
            verifyEnv.responsePattern = v4.verify.responsePattern;
            verifyEnv.expectedResponse = v4.verify.expectedResponse;
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
    public EnvParamsYamlUtilsV5 nextUtil() {
        return (EnvParamsYamlUtilsV5) EnvParamsYamlUtils.BASE_YAML_UTILS.getForVersion(5);
    }

    @Override
    public Void prevUtil() {
        return null;
    }

    @Override
    public String toString(@Nonnull EnvParamsYamlV4 yaml) {
        return getYaml().dump(yaml);
    }

    @Nonnull
    @Override
    public EnvParamsYamlV4 to(@Nonnull String yaml) {
        if (S.b(yaml)) {
            throw new BlankYamlParamsException("'yaml' parameter is blank");
        }
        final EnvParamsYamlV4 p = getYaml().load(yaml);
        return p;
    }

}
