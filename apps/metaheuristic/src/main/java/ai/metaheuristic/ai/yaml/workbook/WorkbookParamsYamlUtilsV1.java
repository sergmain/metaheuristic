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

package ai.metaheuristic.ai.yaml.workbook;

import ai.metaheuristic.api.data.workbook.WorkbookParamsYamlV1;
import ai.metaheuristic.api.data.workbook.WorkbookParamsYamlV2;
import ai.metaheuristic.commons.yaml.YamlUtils;
import ai.metaheuristic.commons.yaml.versioning.AbstractParamsYamlUtils;
import org.springframework.beans.BeanUtils;
import org.yaml.snakeyaml.Yaml;

/**
 * @author Serge
 * Date: 6/17/2019
 * Time: 12:10 AM
 */
public class WorkbookParamsYamlUtilsV1
        extends AbstractParamsYamlUtils<WorkbookParamsYamlV1, WorkbookParamsYamlV2, WorkbookParamsYamlUtilsV2, Void, Void, Void> {

    @Override
    public int getVersion() {
        return 1;
    }

    @Override
    public Yaml getYaml() {
        return YamlUtils.init(WorkbookParamsYamlV1.class);
    }

    @Override
    public WorkbookParamsYamlV2 upgradeTo(WorkbookParamsYamlV1 workbookParams, Long ... vars) {
        WorkbookParamsYamlV2 t = new WorkbookParamsYamlV2();
        BeanUtils.copyProperties(workbookParams, t.workbookYaml);
        if (workbookParams.poolCodes!=null) {
            t.workbookYaml.poolCodes.putAll(workbookParams.poolCodes);
        }
        return t;
    }

    @Override
    public Void downgradeTo(Void yaml) {
        // there isn't any prev version
        return null;
    }

    @Override
    public WorkbookParamsYamlUtilsV2 nextUtil() {
        return (WorkbookParamsYamlUtilsV2)WorkbookParamsYamlUtils.BASE_YAML_UTILS.getForVersion(2);
    }

    @Override
    public Void prevUtil() {
        // there isn't any prev version
        return null;
    }

    @Override
    public String toString(WorkbookParamsYamlV1 yaml) {
        return getYaml().dump(yaml);
    }

    @Override
    public WorkbookParamsYamlV1 to(String s) {
        //noinspection UnnecessaryLocalVariable
        final WorkbookParamsYamlV1 p = getYaml().load(s);
        return p;
    }

}
