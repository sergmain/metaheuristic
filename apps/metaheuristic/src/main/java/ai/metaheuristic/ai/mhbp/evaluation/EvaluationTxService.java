/*
 * Metaheuristic, Copyright (C) 2017-2023, Innovation platforms, LLC
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

package ai.metaheuristic.ai.mhbp.evaluation;

import ai.metaheuristic.ai.mhbp.api.ApiService;
import ai.metaheuristic.ai.mhbp.beans.Evaluation;
import ai.metaheuristic.ai.mhbp.kb.KbService;
import ai.metaheuristic.ai.mhbp.repositories.EvaluationRepository;
import ai.metaheuristic.api.data.OperationStatusRest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * @author Sergio Lissner
 * Date: 4/17/2023
 * Time: 8:47 PM
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Profile("dispatcher")
public class EvaluationTxService {

    public final ApiService apiService;
    public final KbService kbService;
    public final EvaluationRepository evaluationRepository;


    @Transactional
    public OperationStatusRest createEvaluation(String code, String apiId, String[] kbIds, long companyId, long accountId) {
        Evaluation eval = new Evaluation();
        eval.companyId = companyId;
        eval.accountId = accountId;
        eval.createdOn = System.currentTimeMillis();
        eval.apiId = Long.parseLong(apiId);
        eval.chapterIds = List.of(kbIds);
        eval.code = code;

        evaluationRepository.save(eval);

        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    @Transactional
    public OperationStatusRest deleteEvaluationById(Long evaluationId) {
        evaluationRepository.deleteById(evaluationId);
        return OperationStatusRest.OPERATION_STATUS_OK;
    }
}
