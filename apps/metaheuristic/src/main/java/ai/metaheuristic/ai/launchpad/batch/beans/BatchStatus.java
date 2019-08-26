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

import java.beans.Transient;
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

    private static final String DELEMITER_1 = "--------------------------------------------------------------------\n";
    private static final String DELEMITER_2 = "\n====================================================================\n";

    @NoArgsConstructor
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
    private final StatusPart generalStatus = new StatusPart();
    @JsonIgnore
    private final StatusPart okStatus = new StatusPart();
    @JsonIgnore
    private final StatusPart progressStatus = new StatusPart();
    @JsonIgnore
    private final StatusPart errorStatus = new StatusPart();

    @Transient
    public StatusPart getGeneralStatus() {
        return generalStatus;
    }

    @Transient
    public StatusPart getOkStatus() {
        return okStatus;
    }

    @Transient
    public StatusPart getProgressStatus() {
        return progressStatus;
    }

    @Transient
    public StatusPart getErrorStatus() {
        return errorStatus;
    }

    @JsonIgnore
    public final Map<String, String> renameTo = new HashMap<>();

    // must be public for yaml's marshalling
    public boolean ok = false;
    // must be public for yaml's marshalling
    public String status;

    public String getStatus() {
        return status;
    }

    /**
     * Don't forget to call this method before storing in db
     */
    public void init() {
        {
            String generalStr = getGeneralStatus().sb.toString();
            if (!generalStr.isBlank()) {
                status = generalStr + DELEMITER_2;
            }
        }
        {
            String progressStr = getProgressStatus().sb.toString();
            if (!progressStr.isBlank()) {
                status += progressStr + DELEMITER_2;
            }
        }
        {
            String okStr = getOkStatus().sb.toString();
            if (!okStr.isBlank()) {
                status += okStr + DELEMITER_2;
            }
        }
        {
            String errorStr = getErrorStatus().sb.toString();
            if (!errorStr.isBlank()) {
                status += errorStr + DELEMITER_2;
            }
        }

    }
}
