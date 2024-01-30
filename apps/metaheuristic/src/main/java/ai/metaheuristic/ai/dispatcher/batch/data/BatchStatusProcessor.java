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

package ai.metaheuristic.ai.dispatcher.batch.data;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Serge
 * Date: 12/21/2019
 * Time: 2:09 AM
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BatchStatusProcessor {

    public final Map<String, String> renameTo = new HashMap<>();

    // must be public for yaml's marshalling
    public boolean ok = false;

    // must be public for yaml's marshalling
    public String status = "";

    public static class StatusPart {
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

        public String asString() {
            return sb.toString();
        }
    }

    private final StatusPart generalStatus = new StatusPart();
    private final StatusPart okStatus = new StatusPart();
    private final StatusPart progressStatus = new StatusPart();
    private final StatusPart errorStatus = new StatusPart();

    public BatchStatusProcessor addGeneralStatus(String status) {
        generalStatus.add(status);
        return this;
    }

    public BatchStatusProcessor addGeneralStatus(String status, char c) {
        generalStatus.add(status, c);
        return this;
    }

    public StatusPart getGeneralStatus() {
        return generalStatus;
    }

    public StatusPart getOkStatus() {
        return okStatus;
    }

    public StatusPart getProgressStatus() {
        return progressStatus;
    }

    public StatusPart getErrorStatus() {
        return errorStatus;
    }
}
