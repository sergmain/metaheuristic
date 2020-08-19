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

package ai.metaheuristic.api.data.source_code;

import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.BaseParams;
import ai.metaheuristic.api.data.Meta;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.lang.Nullable;

import java.util.List;

/**
 * @author Serge
 * Date: 2/14/2020
 * Time: 11:01 PM
 */
@Data
public class SourceCodeStoredParamsYaml implements BaseParams {

    public final int version=1;

    @Override
    public boolean checkIntegrity() {
        return true;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InternalParams {
        public boolean archived;
        public boolean published;
        public long updatedOn;

        @Nullable
        public List<Meta> metas;

        public void init(boolean archived, boolean published, long updatedOn, @Nullable List<Meta> metas) {
            this.archived = archived;
            this.published = published;
            this.updatedOn = updatedOn;
            this.metas = metas;
        }
    }

    public String source;
    public EnumsApi.SourceCodeLang lang;
    public final InternalParams internalParams = new InternalParams();

}
