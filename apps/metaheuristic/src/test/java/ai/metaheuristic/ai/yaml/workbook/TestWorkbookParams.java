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

import ai.metaheuristic.ai.yaml.communication.station.StationCommParamsYaml;
import ai.metaheuristic.ai.yaml.communication.station.StationCommParamsYamlUtils;
import ai.metaheuristic.api.data.workbook.WorkbookParamsYaml;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author Serge
 * Date: 11/12/2019
 * Time: 3:33 PM
 */
public class TestWorkbookParams {

    @Test
    public void testVersion() {
        assertEquals( new WorkbookParamsYaml().version, WorkbookParamsYamlUtils.BASE_YAML_UTILS.getDefault().getVersion() );
    }
}
