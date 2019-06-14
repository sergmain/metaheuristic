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

package ai.metaheuristic.ai.launchpad.batch.beans;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author Serge
 * Date: 5/30/2019
 * Time: 12:20 AM
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BatchStatus {

    @JsonIgnore
    private final StringBuilder sb = new StringBuilder();
    public boolean ok = false;
    //        public BatchParams batchParams;
    public String status;

    public void add(String status) {
        sb.append(status);
    }

    public void add(String status, char c) {
        sb.append(status);
        sb.append(c);
    }

    public void setStatus(String status) {
        sb.append(status);
    }

    public String getStatus() {
        return status;
    }

    public void add(char c) {
        sb.append(c);
    }

    /**
     * Don't forget to call this method before storing in db
     */
    public void init() {
        status = sb.toString();
    }
}
