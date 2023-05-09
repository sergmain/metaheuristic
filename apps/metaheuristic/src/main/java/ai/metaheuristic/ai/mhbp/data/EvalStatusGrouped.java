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

package ai.metaheuristic.ai.mhbp.data;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.util.Objects;

/**
 * @author Sergio Lissner
 * Date: 3/26/2023
 * Time: 3:25 AM
 */
@NoArgsConstructor
@AllArgsConstructor
public final class EvalStatusGrouped {
    public String sessionId;
    public int total;
    public int normal;
    public int fail;
    public int error;
}
