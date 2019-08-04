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

package ai.metaheuristic.commons.yaml.versioning;

import ai.metaheuristic.api.data.BaseParams;
import org.yaml.snakeyaml.Yaml;

/**
 * @author Serge
 * Date: 6/17/2019
 * Time: 12:17 AM
 */
abstract public class AbstractParamsYamlUtils<CurrT extends BaseParams, NextT extends BaseParams, NextU, PrevT, PrevU, CurrForDownT> {

    public abstract Yaml getYaml();

    public abstract NextT upgradeTo(CurrT yaml, Long ... vars);

    public abstract PrevT downgradeTo(CurrForDownT yaml);

    public abstract NextU nextUtil();

    public abstract PrevU prevUtil();

    public abstract String toString(CurrT yaml);

    public abstract CurrT to(String s);

    public abstract int getVersion();
}
