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

package ai.metaheuristic.ai.mhbp.questions;

import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.mhbp.beans.Answer;
import ai.metaheuristic.ai.mhbp.beans.Chapter;
import ai.metaheuristic.ai.mhbp.beans.Session;
import ai.metaheuristic.ai.mhbp.repositories.AnswerRepository;
import ai.metaheuristic.ai.mhbp.repositories.ChapterRepository;
import ai.metaheuristic.ai.mhbp.yaml.answer.AnswerParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * @author Sergio Lissner
 * Date: 3/22/2023
 * Time: 2:39 AM
 */
@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class QuestionAndAnswerTxService {

    private final AnswerRepository answerRepository;
    private final ChapterRepository chapterRepository;

    @Transactional(readOnly = true)
    public List<QuestionData.PromptWithAnswerWithChapterId> getQuestionToAsk(String chapterId) {

        Stream<QuestionData.PromptWithAnswerWithChapterId> stream = Stream.of(chapterId).map(Long::valueOf)
                .map(id->chapterRepository.findById(id).orElse(null))
                .filter(Objects::nonNull)
                .flatMap(chapter -> chapter.getParts().stream())
                .flatMap(part -> part.getPartParams().prompts.stream().map(p -> QuestionData.PromptWithAnswerWithChapterId.fromPrompt(part.chapterId, part.id, p)));


        return stream.toList();
    }

    @Transactional
    public void process(Session session, Chapter chapter, AnswerParams ap) {
        Answer a = new Answer();
        // not user which values to use for questionCode
        a.questionCode = "q code";

        a.sessionId = session.id;
        a.chapterId = chapter.id;
        a.answeredOn = System.currentTimeMillis();
        a.total = ap.total;
        a.failed = (int)ap.results.stream().filter(o-> o.s==Enums.AnswerStatus.fail).count();
        a.systemError = (int)ap.results.stream().filter(o-> o.s==Enums.AnswerStatus.error).count();

        a.updateParams(ap);

        answerRepository.save(a);
    }

}
