/*
 * Metaheuristic, Copyright (C) 2017-2024, Innovation platforms, LLC
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

import jakarta.persistence.Column;

/**
 * @author Sergio Lissner
 * Date: 4/28/2023
 * Time: 2:49 PM
 */
@AllArgsConstructor
public class SimpleAnswerStats {
    public long id;
    public long sessionId;
    public long chapterId;
    public int total;
    public int failed;
    public int systemError;
}
