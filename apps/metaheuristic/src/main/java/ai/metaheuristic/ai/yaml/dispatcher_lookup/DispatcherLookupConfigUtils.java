/*
 * Metaheuristic, Copyright (C) 2017-2020, Innovation platforms, LLC
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

import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.yaml.YamlUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.InputStream;

@Slf4j
public class DispatcherLookupConfigUtils {

    private static Yaml getYaml() {
        return YamlUtils.init(DispatcherLookupConfig.class);
    }

    public static String toString(DispatcherLookupConfig config) {
        if (config.dispatchers ==null) {
            throw new IllegalStateException("DispatcherLookupConfig is null");
        }
        for (DispatcherLookupConfig.DispatcherLookup signatureConfig : config.dispatchers) {
            if (signatureConfig.signatureRequired && S.b(signatureConfig.publicKey)) {
                throw new IllegalStateException("signatureConfig.publicKey is blank");
            }
            if (signatureConfig.lookupType ==null) {
                throw new IllegalStateException("signatureConfig.type is null");
            }
        }
        return YamlUtils.toString(config, getYaml());
    }

    public static DispatcherLookupConfig to(String s) {
        return (DispatcherLookupConfig) YamlUtils.to(s, getYaml());
    }

    public static DispatcherLookupConfig to(InputStream is) {
        return (DispatcherLookupConfig) YamlUtils.to(is, getYaml());
    }

    public static DispatcherLookupConfig to(File file) {
        return (DispatcherLookupConfig) YamlUtils.to(file, getYaml());
    }
}
