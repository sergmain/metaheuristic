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

package ai.metaheuristic.commons.spi;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * @author Sergio Lissner
 * Date: 12/28/2025
 * Time: 4:14 PM
 */
@Data
@AllArgsConstructor
public class StoredVariable {
    public Long id;
    public String name;
    public boolean nullified;
}
