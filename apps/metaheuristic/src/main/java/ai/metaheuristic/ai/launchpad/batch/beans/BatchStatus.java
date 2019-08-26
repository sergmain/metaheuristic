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

import java.util.HashMap;
import java.util.Map;

/**
 * @author Serge
 * Date: 5/30/2019
 * Time: 12:20 AM
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BatchStatus {

    public static class StatusPart {

        @JsonIgnore
        private final StringBuilder sb = new StringBuilder();

        public void add(String status) {
            sb.append(status);
        }

        public void add(char c) {
            sb.append(c);
        }

        public void add(String status, char c) {
            sb.append(status);
            sb.append(c);
        }
    }

    @JsonIgnore
    public final StatusPart generalStatus = new StatusPart();
    @JsonIgnore
    public final StatusPart okStatus = new StatusPart();
    @JsonIgnore
    public final StatusPart progressStatus = new StatusPart();
    @JsonIgnore
    public final StatusPart errorStatus = new StatusPart();

    @JsonIgnore
    public final Map<String, String> renameTo = new HashMap<>();

    public boolean ok = false;
    private String status;

//    public void setStatus(String status) {
//        sb.append(status);
//    }

    public String getStatus() {
        return status;
    }

    private static final String DELEMITER_1 = "----------------------------------\n";
    /**
     * Don't forget to call this method before storing in db
     */
    private static final String DELEMITER_2 = "\n\n==================================\n";

    public void init() {
        {
            String generalStr = generalStatus.sb.toString();
            if (!generalStr.isBlank()) {
                status = generalStr + DELEMITER_2;
            }
        }
        {
            String progressStr = progressStatus.sb.toString();
            if (!progressStr.isBlank()) {
                status = progressStr + DELEMITER_2;
            }
        }
        {
            String okStr = okStatus.sb.toString();
            if (!okStr.isBlank()) {
                status = okStr + DELEMITER_2;
            }
        }
        {
            String errorStr = errorStatus.sb.toString();
            if (!errorStr.isBlank()) {
                status = errorStr + DELEMITER_2;
            }
        }

    }
}
