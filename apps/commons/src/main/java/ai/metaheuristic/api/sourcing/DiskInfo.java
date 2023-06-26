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

package ai.metaheuristic.api.sourcing;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author Serge
 * Date: 5/8/2019
 * Time: 12:06 AM
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DiskInfo {
    /**
     * A file mask. Can include * and ? as well
     */
    public String mask;

    /**
     * A code for directory. This code must be configured at processor side in file env.yaml
     */
    public String code;

    /**
     * A direct path to file(s), path + mask
     * i.e. \tmp\some-dir\file??.*
     */
    public String path;

}
