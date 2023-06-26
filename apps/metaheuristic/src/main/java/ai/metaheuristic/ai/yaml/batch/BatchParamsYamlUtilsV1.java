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

package ai.metaheuristic.ai.yaml.batch;

import ai.metaheuristic.commons.yaml.YamlUtils;
import ai.metaheuristic.commons.yaml.versioning.AbstractParamsYamlUtils;
import org.springframework.lang.NonNull;
import org.yaml.snakeyaml.Yaml;

/**
 * @author Serge
 * Date: 10/03/2019
 * Time: 6:02 PM
 */
public class BatchParamsYamlUtilsV1
        extends AbstractParamsYamlUtils<BatchParamsYamlV1, BatchParamsYaml, Void, Void, Void, Void> {

    @Override
    public int getVersion() {
        return 1;
    }

    @NonNull
    @Override
    public Yaml getYaml() {
        return YamlUtils.init(BatchParamsYamlV1.class);
    }

    @NonNull
    @Override
    public BatchParamsYaml upgradeTo(@NonNull BatchParamsYamlV1 v2) {
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

    @NonNull
    @Override
    public Void downgradeTo(@NonNull Void yaml) {
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
    public String toString(@NonNull BatchParamsYamlV1 yaml) {
        return getYaml().dump(yaml);
    }

    @NonNull
    @Override
    public BatchParamsYamlV1 to(@NonNull String s) {
        final BatchParamsYamlV1 p = getYaml().load(s);
        return p;
    }

}
