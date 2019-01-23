/*
 * AiAi, Copyright (C) 2017-2019  Serge Maslyukov
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package aiai.ai.yaml.launchpad_lookup;

import aiai.apps.commons.yaml.YamlUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.InputStream;

@Slf4j
public class LaunchpadLookupConfigUtils {

    private static Yaml yaml;

    static {
        yaml = YamlUtils.init(LaunchpadLookupConfig.class);
    }

    public static String toString(LaunchpadLookupConfig config) {
        if (config==null || config.launchpads ==null) {
            throw new IllegalStateException("LaunchpadLookupConfig is null");
        }
        for (LaunchpadLookupConfig.LaunchpadLookup signatureConfig : config.launchpads) {
            if (signatureConfig.signatureRequired && StringUtils.isBlank(signatureConfig.publicKey)) {
                throw new IllegalStateException("signatureConfig.publicKey is blank");
            }
            if (signatureConfig.lookupType ==null) {
                throw new IllegalStateException("signatureConfig.type is null");
            }
        }
        return YamlUtils.toString(config, yaml);
    }

    public static LaunchpadLookupConfig to(String s) {
        return (LaunchpadLookupConfig) YamlUtils.to(s, yaml);
    }

    public static LaunchpadLookupConfig to(InputStream is) {
        return (LaunchpadLookupConfig) YamlUtils.to(is, yaml);
    }

    public static LaunchpadLookupConfig to(File file) {
        return (LaunchpadLookupConfig) YamlUtils.to(file, yaml);
    }
}
