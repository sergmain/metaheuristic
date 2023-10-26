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

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.dispatcher.DispatcherContext;
import ai.metaheuristic.ai.mhbp.api.ApiService;
import ai.metaheuristic.ai.mhbp.api.ApiUtils;
import ai.metaheuristic.ai.mhbp.beans.Api;
import ai.metaheuristic.ai.mhbp.beans.Chapter;
import ai.metaheuristic.ai.mhbp.beans.Evaluation;
import ai.metaheuristic.ai.mhbp.chapter.ChapterService;
import ai.metaheuristic.ai.mhbp.data.EvaluationData;
import ai.metaheuristic.ai.mhbp.events.EvaluateProviderEvent;
import ai.metaheuristic.ai.mhbp.kb.KbService;
import ai.metaheuristic.ai.mhbp.repositories.ApiRepository;
import ai.metaheuristic.ai.mhbp.repositories.ChapterRepository;
import ai.metaheuristic.ai.mhbp.repositories.EvaluationRepository;
import ai.metaheuristic.ai.mhbp.repositories.KbRepository;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.OperationStatusRest;
import ai.metaheuristic.commons.utils.PageUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Sergio Lissner
 * Date: 4/17/2023
 * Time: 1:30 AM
 */
@Service
@Slf4j
@RequiredArgsConstructor(onConstructor_={@Autowired})
@Profile("dispatcher")
public class EvaluationService {

    public final ApiService apiService;
    public final KbService kbService;
    public final ChapterService chapterService;
    public final EvaluationTxService evaluationTxService;
    public final EvaluationRepository evaluationRepository;
    public final ApplicationEventPublisher eventPublisher;
    public final ApiRepository apiRepository;
    public final KbRepository kbRepository;
    public final ChapterRepository chapterRepository;

    public EvaluationData.Evaluations getEvaluations(Pageable pageable, DispatcherContext context) {
        pageable = PageUtils.fixPageSize(20, pageable);

        Page<Evaluation> evaluations = evaluationRepository.findAllByCompanyUniqueId(pageable, context.getCompanyId());
        List<EvaluationData.SimpleEvaluation > list = evaluations.stream().map(EvaluationData.SimpleEvaluation ::new).toList();
        var sorted = list.stream().sorted((o1, o2)->Long.compare(o2.evaluationId, o1.evaluationId)).collect(Collectors.toList());
        return new EvaluationData.Evaluations(new PageImpl<>(sorted, pageable, list.size()));
    }

    public EvaluationData.EvaluationUidsForCompany getEvaluationUidsForCompany(DispatcherContext context) {
        EvaluationData.EvaluationUidsForCompany r = new EvaluationData.EvaluationUidsForCompany();

        r.apis = apiService.getApisAllowedForCompany(context).stream()
                .map(ApiUtils::to)
                .toList();
        r.chapters = chapterService.getChaptersAllowedForCompany(context);
        return r;
    }

    public OperationStatusRest createEvaluation(String code, String apiId, String[] kbIds, long companyId, long accountId) {
        evaluationTxService.createEvaluation(code, apiId, kbIds, companyId, accountId);
        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    public OperationStatusRest evaluate(@Nullable Long evaluationId, DispatcherContext context, int limit) {
        if (evaluationId==null) {
            return OperationStatusRest.OPERATION_STATUS_OK;
        }
        Evaluation evaluation = evaluationRepository.findById(evaluationId).orElse(null);
        if (evaluation == null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "567.150 Evaluation wasn't found, evaluationId: " + evaluationId, null);
        }
        if (evaluation.companyId!=context.getCompanyId()) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "567.200 evaluationId: " + evaluationId);
        }
        Api api = apiRepository.findById(evaluation.apiId).orElse(null);
        if (api==null || api.companyId!=context.getCompanyId()) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "567.220 Reference to API is broken, evaluationId: " + evaluationId);
        }
        if (evaluation.chapterIds.isEmpty()) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "567.240 Reference to KB is empty, evaluationId: " + evaluationId);
        }
        for (String chapterIdStr : evaluation.chapterIds) {
            long chapterId = Long.parseLong(chapterIdStr);
            Chapter chapter = chapterRepository.findById(chapterId).orElse(null);
            if (chapter==null || (chapter.companyId!=context.getCompanyId() && chapter.companyId!=Consts.ID_1)) {
                return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "567.260 Reference to Chapter is broken, evaluationId: " + evaluationId);
            }
        }

        eventPublisher.publishEvent(new EvaluateProviderEvent(evaluationId, limit, context.getAccountId()));
        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    public OperationStatusRest deleteEvaluationById(Long evaluationId, DispatcherContext context) {
        Evaluation evaluation = evaluationRepository.findById(evaluationId).orElse(null);
        if (evaluation == null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "567.150 Evaluation wasn't found, evaluationId: " + evaluationId, null);
        }
        if (evaluation.companyId!=context.getCompanyId()) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "567.200 evaluationId: " + evaluationId);
        }
        return evaluationTxService.deleteEvaluationById(evaluationId);
    }
}
