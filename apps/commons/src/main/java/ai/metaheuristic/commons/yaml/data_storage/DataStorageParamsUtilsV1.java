/*
 * Metaheuristic, Copyright (C) 2017-2025, Innovation platforms, LLC
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

package ai.metaheuristic.commons.yaml.data_storage;

import ai.metaheuristic.api.data_storage.DataStorageParams;
import ai.metaheuristic.api.data_storage.DataStorageParamsV1;
import ai.metaheuristic.api.sourcing.DiskInfo;
import ai.metaheuristic.api.sourcing.GitInfo;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.exceptions.BlankYamlParamsException;
import ai.metaheuristic.commons.yaml.YamlUtils;
import ai.metaheuristic.commons.yaml.versioning.AbstractParamsYamlUtils;
import org.jspecify.annotations.NonNull;
import org.yaml.snakeyaml.Yaml;


/**
 * @author Serge
 * Date: 4/11/2020
 * Time: 10:03 PM
 */
@SuppressWarnings("DuplicatedCode")
public class DataStorageParamsUtilsV1
        extends AbstractParamsYamlUtils<DataStorageParamsV1, DataStorageParams, Void, Void, Void, Void> {

    @Override
    public int getVersion() {
        return 1;
    }

    @NonNull
    @Override
    public Yaml getYaml() {
        return YamlUtils.init(DataStorageParamsV1.class);
    }

    @NonNull
    @Override
    public DataStorageParams upgradeTo(DataStorageParamsV1 v1) {
        v1.checkIntegrity();

        DataStorageParams t = new DataStorageParams();
        if (v1.git!=null) {
            t.git = new GitInfo(v1.git.repo, v1.git.branch, v1.git.commit, v1.git.path);
        }
        if (v1.disk!=null) {
            t.disk = new DiskInfo(v1.disk.mask, v1.disk.code, v1.disk.path);
        }
        t.name = v1.name;
        t.sourcing = v1.sourcing;
        t.type = v1.type;
        t.size = v1.size;

        t.checkIntegrity();
        return t;
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
    public String toString(@NonNull DataStorageParamsV1 params) {
        return getYaml().dump(params);
    }

    @NonNull
    @Override
    public DataStorageParamsV1 to(@NonNull String yaml) {
        if (S.b(yaml)) {
            throw new BlankYamlParamsException("'yaml' parameter is blank");
        }
        //noinspection UnnecessaryLocalVariable
        final DataStorageParamsV1 p = getYaml().load(yaml);
        return p;
    }

}
