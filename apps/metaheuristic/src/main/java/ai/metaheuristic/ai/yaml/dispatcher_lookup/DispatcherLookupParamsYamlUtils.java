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

package ai.metaheuristic.ai.yaml.dispatcher_lookup;

import ai.metaheuristic.ai.yaml.metadata.MetadataParamsYaml;
import ai.metaheuristic.ai.yaml.metadata.MetadataParamsYamlUtilsV1;
import ai.metaheuristic.ai.yaml.metadata.MetadataParamsYamlUtilsV2;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.yaml.YamlUtils;
import ai.metaheuristic.commons.yaml.versioning.BaseYamlUtils;
import lombok.extern.slf4j.Slf4j;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.InputStream;
import java.util.Map;

@Slf4j
public class DispatcherLookupParamsYamlUtils {

    private static final DispatcherLookupParamsYamlUtilsV1 YAML_UTILS_V_1 = new DispatcherLookupParamsYamlUtilsV1();
    private static final DispatcherLookupParamsYamlUtilsV2 YAML_UTILS_V_2 = new DispatcherLookupParamsYamlUtilsV2();
    private static final DispatcherLookupParamsYamlUtilsV2 DEFAULT_UTILS = YAML_UTILS_V_2;

    public static final BaseYamlUtils<DispatcherLookupParamsYaml> BASE_YAML_UTILS = new BaseYamlUtils<>(
            Map.of(
                    1, YAML_UTILS_V_1,
                    2, YAML_UTILS_V_2
            ),
            DEFAULT_UTILS
    );

/*    private static Yaml getYaml() {
        return YamlUtils.init(DispatcherLookupParamsYaml.class);
    }

    public static String toString(DispatcherLookupParamsYaml config) {
        if (config.dispatchers ==null) {
            throw new IllegalStateException("DispatcherLookupConfig is null");
        }
        for (DispatcherLookupParamsYaml.DispatcherLookup signatureConfig : config.dispatchers) {
            if (signatureConfig.signatureRequired && S.b(signatureConfig.publicKey)) {
                throw new IllegalStateException("signatureConfig.publicKey is blank");
            }
            if (signatureConfig.lookupType ==null) {
                throw new IllegalStateException("signatureConfig.type is null");
            }
        }
        return YamlUtils.toString(config, getYaml());
    }

    public static DispatcherLookupParamsYaml to(String s) {
        return (DispatcherLookupParamsYaml) YamlUtils.to(s, getYaml());
    }

    public static DispatcherLookupParamsYaml to(InputStream is) {
        return (DispatcherLookupParamsYaml) YamlUtils.to(is, getYaml());
    }

    public static DispatcherLookupParamsYaml to(File file) {
        return (DispatcherLookupParamsYaml) YamlUtils.to(file, getYaml());
    }

    private void fix(DispatcherLookupParamsYaml cfg) {
        if (cfg.dispatchers!=null ) {
            for (DispatcherLookupParamsYaml.DispatcherLookup dispatcher : cfg.dispatchers) {
                if (dispatcher.asset!=null && S.b(dispatcher.asset.publicKey)) {
                    dispatcher.asset.publicKey = dispatcher.publicKey;
                }
            }
        }
    }*/
}
