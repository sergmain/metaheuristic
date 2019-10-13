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

import ai.metaheuristic.api.data.workbook.WorkbookParamsYaml;
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
public class WorkbookParamsYamlUtilsV2
        extends AbstractParamsYamlUtils<WorkbookParamsYamlV2, WorkbookParamsYaml, Void, Void, Void, Void> {

    @Override
    public int getVersion() {
        return 2;
    }

    public Yaml getYaml() {
        return YamlUtils.init(WorkbookParamsYamlV2.class);
    }

    @Override
    public WorkbookParamsYaml upgradeTo(WorkbookParamsYamlV2 yaml, Long ... vars) {
        WorkbookParamsYaml t = new WorkbookParamsYaml();

        // right now we don't need to convert Graph because it has only one version of structure
        // so just copying of graph field is Ok
        BeanUtils.copyProperties(yaml.workbookYaml, t.workbookYaml);
        if (yaml.workbookYaml.poolCodes!=null) {
            t.workbookYaml.poolCodes.putAll(yaml.workbookYaml.poolCodes);
        }
        t.graph = yaml.graph;
        return t;
    }

    @Override
    public Void downgradeTo(Void yaml) {
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

    public String toString(WorkbookParamsYamlV2 yaml) {
        return getYaml().dump(yaml);
    }

    public WorkbookParamsYamlV2 to(String s) {
        final WorkbookParamsYamlV2 p = getYaml().load(s);
        return p;
    }

}
