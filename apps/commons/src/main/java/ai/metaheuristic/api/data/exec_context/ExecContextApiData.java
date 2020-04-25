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
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.domain.Slice;

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
}
