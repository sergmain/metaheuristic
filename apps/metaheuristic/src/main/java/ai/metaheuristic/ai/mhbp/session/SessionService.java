/*
 * Metaheuristic, Copyright (C) 2017-2025, Innovation platforms, LLC
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
import ai.metaheuristic.ai.mhbp.beans.Api;
import ai.metaheuristic.ai.mhbp.beans.Evaluation;
import ai.metaheuristic.ai.mhbp.beans.Session;
import ai.metaheuristic.ai.mhbp.data.ErrorData;
import ai.metaheuristic.ai.mhbp.data.SessionData;
import ai.metaheuristic.ai.mhbp.data.SimpleAnswerStats;
import ai.metaheuristic.ai.mhbp.repositories.*;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.account.UserContext;
import ai.metaheuristic.commons.utils.PageUtils;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author Sergio Lissner
 * Date: 3/26/2023
 * Time: 3:36 AM
 */
@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class SessionService {

    private final SessionTxService sessionTxService;
    private final SessionRepository sessionRepository;
    private final AnswerRepository answerRepository;
    private final EvaluationRepository evaluationRepository;
    private final ApiRepository apiRepository;
    private final ChapterRepository chapterRepository;

    @AllArgsConstructor
    @NoArgsConstructor
    public static class EvalsDesc {
        public long evaluationId;
        public String apiInfo;
        public List<String> chapters;
    }

    public SessionData.SessionStatuses getStatuses(Pageable pageable) {
        pageable = PageUtils.fixPageSize(10, pageable);

        Map<Long, Session> sessions = sessionRepository.getSessions(pageable).stream()
                .collect(Collectors.toMap(Session::getId, Function.identity()));

        if (sessions.isEmpty()) {
            return new SessionData.SessionStatuses(Page.empty(pageable));
        }

        final List<Long> sessionIds = sessions.keySet().stream().sorted(Comparator.reverseOrder()).toList();
        final List<SimpleAnswerStats> statuses = answerRepository.getStatusesJpql(sessionIds);
        List<SessionData.SessionStatus> list = new ArrayList<>();
        Map<Long, EvalsDesc> localCache = new HashMap<>();
        for (Long sessionId : sessionIds) {
            Session s = sessions.get(sessionId);
            List<SimpleAnswerStats> sessionStats = statuses.stream().filter(o->o.sessionId==sessionId).toList();
            long total = sessionStats.stream().mapToLong(o->o.total).sum();
            long failed = sessionStats.stream().mapToLong(o->o.failed).sum();
            long error = sessionStats.stream().mapToLong(o->o.systemError).sum();

            float normalPercent = 0;
            float failPercent = 0;
            float errorPercent = 0;
            if (total!=0) {
                normalPercent = ((float)(total-failed - error)) / total;
                failPercent = (float)failed / total;
                errorPercent = (float)error / total;
            }
            long evaluationId = s.evaluationId;

            Evaluation e = evaluationRepository.findById(evaluationId).orElse(null);
            EvalsDesc evalsDesc = localCache.computeIfAbsent(evaluationId, evaluationId1 -> getEvalsDesc(e));
            SessionData.SessionStatus es = new SessionData.SessionStatus(
                    s.id, s.startedOn, s.finishedOn, Enums.SessionStatus.to(s.status).toString(), null,
                    normalPercent, failPercent, errorPercent, s.providerCode, evalsDesc.apiInfo,  evalsDesc.evaluationId, String.join(", ", evalsDesc.chapters)
            );
            list.add(es);
        }
//        var sorted = list.stream().sorted((o1, o2)->Long.compare(o2.sessionId(), o1.sessionId())).collect(Collectors.toList());
        return new SessionData.SessionStatuses(new PageImpl<>(list, pageable, list.size()));
    }

    public static final String UNKNOWN = "<unknown>";

    private EvalsDesc getEvalsDesc(@Nullable Evaluation e) {
        if (e==null) {
            return new EvalsDesc(-1, UNKNOWN, List.of("<unknown>"));
        }
        Api api = apiRepository.findById(e.apiId).orElse(null);
        if (api==null) {
            return new EvalsDesc(-1, UNKNOWN, List.of("<unknown>"));
        }
        if (S.b(api.code)) {
            return new EvalsDesc(-1, UNKNOWN, List.of("<unknown>"));
        }
        EvalsDesc evalsDesc = new EvalsDesc(e.id, api.code, new ArrayList<>());

        for (String chapterId : e.chapterIds) {
            String chapterCode = chapterRepository.findById(Long.parseLong(chapterId)).map(o->o.code).orElse(null);
            if (!S.b(chapterCode)) {
                evalsDesc.chapters.add(chapterCode);
            }
        }
        if (evalsDesc.chapters.isEmpty()) {
            evalsDesc.chapters.add("<Error retrieving KBs' chapters >");
        }
        return evalsDesc;
    }

    public ErrorData.ErrorsResult getErrors(Pageable pageable, Long sessionId, UserContext context) {
        Session s = sessionRepository.findById(sessionId).orElse(null);
        if (s==null) {
            return new ErrorData.ErrorsResult("Session wasn't found");
        }
        if (s.companyId!=context.getCompanyId()) {
            return new ErrorData.ErrorsResult("Wrong sessionId");
        }
        ErrorData.ErrorsResult result = sessionTxService.getErrors(pageable, sessionId);
        return result;
    }
}
