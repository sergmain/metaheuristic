/*
 * Metaheuristic, Copyright (C) 2017-2019  Serge Maslyukov
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

package ai.metaheuristic.ai.yaml.batch;

import ai.metaheuristic.commons.yaml.YamlUtils;
import ai.metaheuristic.commons.yaml.versioning.AbstractParamsYamlUtils;
import org.yaml.snakeyaml.Yaml;

/**
 * @author Serge
 * Date: 10/03/2019
 * Time: 6:02 PM
 */
public class BatchParamsYamlUtilsV2
        extends AbstractParamsYamlUtils<BatchParamsYamlV2, BatchParamsYaml, Void, Void, Void, Void> {

    @Override
    public int getVersion() {
        return 2;
    }

    @Override
    public Yaml getYaml() {
        return YamlUtils.init(BatchParamsYamlV2.class);
    }

    @Override
    public BatchParamsYaml upgradeTo(BatchParamsYamlV2 v2, Long ... vars) {
        v2.checkIntegrity();
        BatchParamsYaml t = new BatchParamsYaml();
        if( v2.batchStatus!=null ) {
            t.batchStatus = new BatchParamsYaml.BatchStatus();
            t.batchStatus.ok = v2.batchStatus.ok;
            t.batchStatus.status = v2.batchStatus.status;
        }
        t.ok = v2.ok;
        t.username = v2.username;
        t.checkIntegrity();
        return t;
    }

    @Override
    public Void downgradeTo(Void yaml) {
        // not supported
        return null;
    }

    @Override
    public Void nextUtil() {
        return null;
    }

    @Override
    public Void prevUtil() {
        // not supported
        return null;
    }

    @Override
    public String toString(BatchParamsYamlV2 yaml) {
        return getYaml().dump(yaml);
    }

    @Override
    public BatchParamsYamlV2 to(String s) {
        //noinspection UnnecessaryLocalVariable
        final BatchParamsYamlV2 p = getYaml().load(s);
        return p;
    }

}
