/*
 * Metaheuristic, Copyright (C) 2017-2020  Serge Maslyukov
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

package ai.metaheuristic.api.data.exec_context;

import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.BaseDataClass;
import lombok.*;
import org.springframework.data.domain.Slice;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Serge
 * Date: 4/20/2020
 * Time: 12:04 AM
 */
public class ExecContextApiData {

    @Data
    @EqualsAndHashCode(callSuper = false)
    public static class ExecContextsResult extends BaseDataClass {
        public Long sourceCodeId;
        public String sourceCodeUid;
        public boolean sourceCodeValid;
        public EnumsApi.SourceCodeType sourceCodeType;
        public Slice<ExecContextsListItem> instances;
    }

    @Data
    @NoArgsConstructor
    public static class StateCell {
        public boolean empty = true;
        public Long taskId;
        public String state;
        public String context;

        public StateCell(Long taskId, String state, String context) {
            this.empty = false;
            this.taskId = taskId;
            this.state = state;
            this.context = context;
        }
    }

    @Data
    @AllArgsConstructor
    public static class LineHeader {
        public String context;
//        public String functionCode;
    }

    @Data
    @AllArgsConstructor
    public static class ColumnHeader {
        public String process;
        public String functionCode;
    }

    @Data
    public static class LineWithState {
        public String context;
        public StateCell[] cells;
    }

    @Data
    @EqualsAndHashCode(callSuper = false)
    public static class ExecContextStateResult extends BaseDataClass {
        public Long sourceCodeId;
        public String sourceCodeUid;
        public boolean sourceCodeValid;
        public EnumsApi.SourceCodeType sourceCodeType;
        public ColumnHeader[] header;
        public LineWithState[] lines;
    }

}
