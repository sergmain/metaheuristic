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

package ai.metaheuristic.ai.mhbp.session;

import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.dispatcher.DispatcherContext;
import ai.metaheuristic.ai.mhbp.beans.Answer;
import ai.metaheuristic.ai.mhbp.beans.Api;
import ai.metaheuristic.ai.mhbp.beans.Evaluation;
import ai.metaheuristic.ai.mhbp.beans.Session;
import ai.metaheuristic.ai.mhbp.data.ErrorData;
import ai.metaheuristic.ai.mhbp.repositories.AnswerRepository;
import ai.metaheuristic.ai.mhbp.repositories.SessionRepository;
import ai.metaheuristic.ai.mhbp.yaml.answer.AnswerParams;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.OperationStatusRest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Sergio Lissner
 * Date: 3/22/2023
 * Time: 2:51 AM
 */
@Service
@Profile("dispatcher")
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class SessionTxService {

    private final SessionRepository sessionRepository;
    private final AnswerRepository answerRepository;

    @Transactional
    public Session create(Evaluation evaluation, Api api, Long accountId) {
        Session s = new Session();
        s.evaluationId = evaluation.id;
        s.companyId = evaluation.companyId;
        s.accountId = accountId;
        s.startedOn = System.currentTimeMillis();
        s.providerCode = api.id+":"+api.code;
        s.status = Enums.SessionStatus.created.code;

        sessionRepository.save(s);
        return s;
    }

    @Transactional
    public void finish(Session s, Enums.SessionStatus status) {
        s.finishedOn = System.currentTimeMillis();
        s.status = status.code;
        sessionRepository.save(s);
    }

    @Transactional
    public OperationStatusRest deleteSessionById(@Nullable Long sessionId, DispatcherContext context) {
        if (sessionId==null) {
            return OperationStatusRest.OPERATION_STATUS_OK;
        }
        Session sourceCode = sessionRepository.findById(sessionId).orElse(null);
        if (sourceCode == null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.OK,
                    "566.200 session wasn't found, sessionId: " + sessionId, null);
        }
        sessionRepository.deleteById(sessionId);
        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    @Transactional(readOnly = true)
    public ErrorData.ErrorsResult getErrors(Pageable pageable, Long sessionId)  {
        Page<Answer> answers = answerRepository.findAllBySessionId(pageable, sessionId);
        List<ErrorData.SimpleError> list = answers.stream().flatMap(answer -> {
            AnswerParams ap = answer.getAnswerParams();
            return ap.getResults().stream()
                    .map(o->new ErrorData.SimpleError(answer.id, answer.sessionId, o.p, o.e, o.a, o.r));
        })
        .toList();
        var sorted = list.stream().sorted((o1, o2)->Long.compare(o2.id, o1.id)).collect(Collectors.toList());
        return new ErrorData.ErrorsResult(new PageImpl<>(sorted, pageable, list.size()));
    }

}
