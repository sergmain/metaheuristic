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

import ai.metaheuristic.ai.mhbp.beans.Evaluation;
import ai.metaheuristic.api.data.BaseDataClass;
import lombok.*;
import org.springframework.data.domain.Slice;

import java.util.List;

/**
 * @author Sergio Lissner
 * Date: 4/17/2023
 * Time: 1:26 AM
 */
public class EvaluationData {

    @Data
    @EqualsAndHashCode(callSuper = false)
    @AllArgsConstructor
    @NoArgsConstructor
    public static class EvaluationUidsForCompany extends BaseDataClass {
        public List<ApiData.ApiUid> apis;
        public List<SimpleChapterUid> chapters;
    }

    public static class SimpleEvaluation {
        public long evaluationId;
        public long createdOn;
        public String code;

        public SimpleEvaluation(Evaluation eval) {
            this.evaluationId = eval.id;
            this.code = eval.code;
            this.createdOn = eval.createdOn;
        }
    }

    @RequiredArgsConstructor
    public static class Evaluations extends BaseDataClass {
        public final Slice<SimpleEvaluation> evaluations;
    }

}
