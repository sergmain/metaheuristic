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

package ai.metaheuristic.ai.batch.data;

import ai.metaheuristic.ai.batch.beans.Batch;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author Serge
 * Date: 6/1/2019
 * Time: 4:21 PM
 */
public final class BatchData {
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProcessResourceItem {
        public Batch batch;
        public String planCode;
        public String execStateStr;
        public int execState;
        public boolean ok;
    }
}
