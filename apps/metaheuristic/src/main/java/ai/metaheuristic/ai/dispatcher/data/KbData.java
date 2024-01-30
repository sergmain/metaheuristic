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

package ai.metaheuristic.ai.dispatcher.data;

import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.api.data.BaseDataClass;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Slice;

import java.util.Collections;

/**
 * @author Sergio Lissner
 * Date: 4/16/2023
 * Time: 12:29 AM
 */
public class KbData {

    public interface KbGit {
        String getRepo();
        String getBranch();
        String getCommit();
    }

    public static class SimpleKb {
        public long id;
        public String code;
        public String params;
        public boolean editable;
        public String status;

        public SimpleKb(ai.metaheuristic.ai.mhbp.beans.Kb kb) {
            this.id = kb.id;
            this.code = kb.code;
            this.status = Enums.KbStatus.to(kb.status).toString();
            this.params = kb.getParams();
        }

        public static SimpleKb editableSimpleKb(ai.metaheuristic.ai.mhbp.beans.Kb kb) {
            SimpleKb simpleKb = new SimpleKb(kb);
            simpleKb.editable = true;
            return simpleKb;
        }
    }

    @RequiredArgsConstructor
    public static class Kbs extends BaseDataClass {
        public final Slice<SimpleKb> kbs;
    }

    @Data
    @EqualsAndHashCode(callSuper = false)
    @NoArgsConstructor
    public static class Kb extends BaseDataClass {
        public SimpleKb kb;

        public Kb(String errorMessage) {
            this.errorMessages = Collections.singletonList(errorMessage);
        }

        public Kb(SimpleKb kb, String errorMessage) {
            this.kb = kb;
            this.errorMessages = Collections.singletonList(errorMessage);
        }

        public Kb(SimpleKb kb) {
            this.kb = kb;
        }
    }

}
