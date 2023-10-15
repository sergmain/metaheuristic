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

package ai.metaheuristic.commons.yaml.bundle_cfg;

import ai.metaheuristic.api.EnumsApi;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Sergio Lissner
 * Date: 10/14/2023
 * Time: 4:40 PM
 */
public class BundleCfgYamlUtilsTest {

    @Test
    public void test_toString() {

        BundleCfgYaml cfg = new BundleCfgYaml();
        cfg.bundleConfig.add(new BundleCfgYaml.BundleConfig("f", EnumsApi.BundleItemType.function));
        cfg.bundleConfig.add(new BundleCfgYaml.BundleConfig("s", EnumsApi.BundleItemType.sourceCode));

        String yaml = BundleCfgYamlUtils.UTILS.toString(cfg);

        BundleCfgYaml cfg1 = BundleCfgYamlUtils.UTILS.to(yaml);

        assertEquals(2, cfg.bundleConfig.size());

        BundleCfgYaml.BundleConfig entry;

        entry = cfg.bundleConfig.get(0);
        assertEquals("f", entry.path);
        assertEquals(EnumsApi.BundleItemType.function, entry.type);

        entry = cfg.bundleConfig.get(1);
        assertEquals("s", entry.path);
        assertEquals(EnumsApi.BundleItemType.sourceCode, entry.type);

        System.out.println(yaml);

    }
}
