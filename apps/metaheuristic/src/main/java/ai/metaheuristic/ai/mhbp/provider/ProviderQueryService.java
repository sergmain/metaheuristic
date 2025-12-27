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

package ai.metaheuristic.ai.mhbp.provider;

import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.mhbp.beans.Api;
import ai.metaheuristic.ai.mhbp.beans.Chapter;
import ai.metaheuristic.ai.mhbp.beans.Evaluation;
import ai.metaheuristic.ai.mhbp.beans.Session;
import ai.metaheuristic.ai.mhbp.data.ApiData;
import ai.metaheuristic.ai.mhbp.data.NluData;
import ai.metaheuristic.ai.mhbp.events.EvaluateProviderEvent;
import ai.metaheuristic.ai.mhbp.questions.QuestionAndAnswerService;
import ai.metaheuristic.ai.mhbp.questions.QuestionData;
import ai.metaheuristic.ai.mhbp.repositories.ApiRepository;
import ai.metaheuristic.ai.mhbp.repositories.ChapterRepository;
import ai.metaheuristic.ai.mhbp.repositories.EvaluationRepository;
import ai.metaheuristic.ai.mhbp.session.SessionTxService;
import ai.metaheuristic.ai.mhbp.yaml.answer.AnswerParams;
import ai.metaheuristic.commons.yaml.scheme.ApiScheme;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.utils.threads.ThreadUtils;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Stream;

import static ai.metaheuristic.api.EnumsApi.OperationStatus.ERROR;
import static ai.metaheuristic.api.EnumsApi.OperationStatus.OK;

/**
 * @author Sergio Lissner
 * Date: 3/19/2023
 * Time: 9:31 PM
 */
@Slf4j
@Service
@Profile("dispatcher")
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class ProviderQueryService {

    private final Globals globals;
    private final ProviderApiSchemeService providerService;
    private final EvaluationRepository evaluationRepository;
    private final ApiRepository apiRepository;
    private final ChapterRepository chapterRepository;
    private final QuestionAndAnswerService questionAndAnswerService;
    private final SessionTxService sessionTxService;

    public void evaluateProvider(EvaluateProviderEvent event) {
        final AtomicReference<Session> s = new AtomicReference<>(null);
        try {
            Evaluation evaluation = evaluationRepository.findById(event.evaluationId()).orElse(null);
            if (evaluation==null) {
                return;
            }
            Api api = apiRepository.findById(evaluation.apiId).orElse(null);
            if (api==null) {
                return;
            }
            s.set(sessionTxService.create(evaluation, api, event.accountId()));

            log.debug("call EvaluateProviderService.evaluateProvider({})", event.evaluationId());
            Stream<QuestionData.PromptWithAnswerWithChapterId> questions = questionAndAnswerService.getQuestionToAsk(evaluation.chapterIds, event.limit());

            askQuestions(s, api, questions);
            sessionTxService.finish(s.get(), Enums.SessionStatus.finished);
        } catch (Throwable th) {
            log.error("417.020 Error, need to investigate ", th);
            if (s.get()!=null) {
                sessionTxService.finish(s.get(), Enums.SessionStatus.finished_with_error);
            }
        }
    }

    public record PromptWithAnswer(QuestionData.QuestionWithAnswerToAsk prompt, ProviderData.QuestionAndAnswer answer, Enums.AnswerStatus status) {}

    @AllArgsConstructor
    public static class ChapterWithResults {
        public Chapter c;
        public final List<PromptWithAnswer> answers = Collections.synchronizedList(new ArrayList<>());
        public final AtomicInteger currErrors = new AtomicInteger();

    }

    private void askQuestions(AtomicReference<Session> s, Api api, Stream<QuestionData.PromptWithAnswerWithChapterId> questions) throws InterruptedException {
        long mills = System.currentTimeMillis();
        ConcurrentHashMap<Long, ChapterWithResults> chapterCache = new ConcurrentHashMap<>();
        long endMills;
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            AtomicInteger currTotalErrors = new AtomicInteger();
            final List<Future<?>> f = new ArrayList<>();
            questions.forEach(question -> {
                Thread t = new Thread(() -> makeQuery(api, question, currTotalErrors, chapterCache), "ProviderQueryService-" + ThreadUtils.nextThreadNum());
                f.add(executor.submit(t));
            });
            // replace with CompletableFuture?
            ThreadUtils.waitTaskCompleted(f, 20);
            endMills = ThreadUtils.execStat(mills, f.size());
        }

        for (ChapterWithResults withResults : chapterCache.values()) {
            AnswerParams ap = new AnswerParams();
            ap.total = withResults.answers.size();
            ap.processingMills = endMills - mills;
            for (PromptWithAnswer qaa : withResults.answers) {
                if (qaa.status==Enums.AnswerStatus.normal || qaa.answer.a()==null || qaa.answer.a().processedAnswer.rawAnswerFromAPI().type().binary) {
                    continue;
                }
                AnswerParams.Result r = new AnswerParams.Result();
                r.p = qaa.answer.q();
                r.a = qaa.answer.a().processedAnswer.answer();
                r.e = qaa.prompt.a();
                r.s = qaa.status;

                r.r = qaa.answer.a().processedAnswer.rawAnswerFromAPI().raw();
                if (r.s==Enums.AnswerStatus.error) {
                    r.r = qaa.answer.error();
                }
                ap.results.add(r);
            }
            questionAndAnswerService.process(s.get(), withResults.c, ap);
        }
    }

    private void makeQuery(Api api, QuestionData.PromptWithAnswerWithChapterId question, AtomicInteger currTotalErrors, ConcurrentHashMap<Long, ChapterWithResults> chapterCache) {
        if (currTotalErrors.get() >= globals.mhbp.max.errorsPerEvaluation) {
            return;
        }
        ChapterWithResults withResults = chapterCache.computeIfAbsent(question.chapterId(),
                (chapterId)->chapterRepository.findById(chapterId).map(ChapterWithResults::new).orElse(null));

        if (withResults==null) {
            return;
        }
        if (withResults.currErrors.get() >= globals.mhbp.max.errorsPerPart) {
            return;
        }

        ProviderData.QueriedData queriedData = new ProviderData.QueriedData(question.prompt().q(), null);
        ProviderData.QuestionAndAnswer answer = processQuery(api, queriedData, ProviderQueryService::asQueriedInfoWithError);

        Enums.AnswerStatus status;
        if (answer.status()==OK) {
            status = answer.a()!=null && answer.a().processedAnswer.answer()!=null && question.prompt().a().equals(answer.a().processedAnswer.answer().strip())
                    ? Enums.AnswerStatus.normal
                    : Enums.AnswerStatus.fail;
        }
        else {
            status = Enums.AnswerStatus.error;
        }
        if (status!=Enums.AnswerStatus.error) {
            currTotalErrors.incrementAndGet();
            withResults.currErrors.incrementAndGet();
        }
        withResults.answers.add(new PromptWithAnswer(question.prompt(), answer, status));
    }


    public static ApiData.QueriedInfoWithError asQueriedInfoWithError(ProviderData.QueriedData queriedData) {
        final NluData.QueriedPrompt queriedInfo = new NluData.QueriedPrompt(queriedData.queryText());
        return new ApiData.QueriedInfoWithError(queriedInfo, null);
    }

    // TODO 2023-06-01 why we need function getQueriedInfoWithErrorFunc() ?
    public ProviderData.QuestionAndAnswer processQuery(Api api, ProviderData.QueriedData queriedData,
                                                       Function<ProviderData.QueriedData, ApiData.QueriedInfoWithError> getQueriedInfoWithErrorFunc) {
        try {
            if (S.b(queriedData.queryText())) {
                return new ProviderData.QuestionAndAnswer(ERROR, "Required parameter wasn't specified");
            }
            final ApiData.QueryResult result = processInternal(api, queriedData, getQueriedInfoWithErrorFunc);
            if (result.error!=null ) {
                return new ProviderData.QuestionAndAnswer(ERROR, result.error.error);
            }
            return new ProviderData.QuestionAndAnswer(queriedData.queryText(), result);
        }
        catch (Throwable e) {
            log.error("Error", e);
            return new ProviderData.QuestionAndAnswer(ERROR, e.getMessage());
        }
    }

    private ApiData.QueryResult processInternal(Api api, ProviderData.QueriedData queriedData,
                                                Function<ProviderData.QueriedData, ApiData.QueriedInfoWithError> getQueriedInfoWithErrorFunc) {
        ApiData.QueryResult queryResult;
        if (queriedData.queryText().length()>globals.mhbp.max.promptLength) {
            return ApiData.QueryResult.asError("Text of prompt is too long, max " + globals.mhbp.max.promptLength + " chars. actual length is " + queriedData.queryText().length(),
                    Enums.QueryResultErrorType.query_too_long);
        }
        try {
            ApiData.QueriedInfoWithError queriedInfoWithError = getQueriedInfoWithErrorFunc.apply(queriedData);
            if (queriedInfoWithError.error!=null) {
                queryResult = new ApiData.QueryResult(null, false, queriedInfoWithError.error);
            }
            else if (queriedInfoWithError.queriedInfo!=null) {
                ApiData.SchemeAndParamResult r = providerService.queryProviders(api, queriedData, queriedInfoWithError.queriedInfo);

                ApiScheme.Response response = api.getApiScheme().scheme.response;
                if (response==null) {
                    throw new IllegalStateException();
                }
                if (r.status!=OK) {
                    return ApiData.QueryResult.asError(r.errorText, Enums.QueryResultErrorType.server_error);
                }
                ApiData.ProcessedAnswerFromAPI processedAnswer = ProviderQueryUtils.processAnswerFromApi(r.rawAnswerFromAPI, response);
                queryResult = new ApiData.QueryResult(processedAnswer, true);
            }
            else {
                throw new IllegalStateException();
            }
        }
        catch (Throwable th) {
            log.error("Error", th);
            queryResult = ApiData.QueryResult.asError("Query can't be processed at this time, server error: " + th.getMessage(), Enums.QueryResultErrorType.server_error);
        }
        return queryResult;
    }

}
