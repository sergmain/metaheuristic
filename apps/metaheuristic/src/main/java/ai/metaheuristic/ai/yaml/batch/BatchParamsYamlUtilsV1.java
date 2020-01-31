/*
 * Metaheuristic, Copyright (C) 2017-2020  Serge Maslyukov
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
 * Date: 8/29/2019
 * Time: 6:02 PM
 */
public class BatchParamsYamlUtilsV1
        extends AbstractParamsYamlUtils<BatchParamsYamlV1, BatchParamsYamlV2, BatchParamsYamlUtilsV2, Void, Void, Void> {

    @Override
    public int getVersion() {
        return 1;
    }

    @Override
    public Yaml getYaml() {
        return YamlUtils.init(BatchParamsYamlV1.class);
    }

    @Override
    public BatchParamsYamlV2 upgradeTo(BatchParamsYamlV1 v1, Long ... vars) {
        v1.checkIntegrity();
        BatchParamsYamlV2 t = new BatchParamsYamlV2();
        if( v1.batchStatus!=null ) {
            t.batchStatus = new BatchParamsYamlV2.BatchStatusV2();
            t.batchStatus.ok = v1.batchStatus.ok;
            t.batchStatus.status = v1.batchStatus.status;
        }
        t.ok = v1.ok;
        t.checkIntegrity();
        return t;
    }

    @Override
    public Void downgradeTo(Void yaml) {
        return null;
    }

    @Override
    public BatchParamsYamlUtilsV2 nextUtil() {
        return (BatchParamsYamlUtilsV2) BatchParamsYamlUtils.BASE_YAML_UTILS.getForVersion(2);
    }

    @Override
    public Void prevUtil() {
        return null;
    }

    @Override
    public String toString(BatchParamsYamlV1 yaml) {
        return getYaml().dump(yaml);
    }

    @Override
    public BatchParamsYamlV1 to(String s) {
        //noinspection UnnecessaryLocalVariable
        final BatchParamsYamlV1 p = getYaml().load(s);
        return p;
    }

}
