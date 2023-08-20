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

import ai.metaheuristic.ai.mhbp.beans.Chapter;
import ai.metaheuristic.ai.mhbp.beans.Session;
import ai.metaheuristic.ai.mhbp.yaml.answer.AnswerParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

/**
 * @author Sergio Lissner
 * Date: 3/19/2023
 * Time: 11:01 PM
 */
@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class QuestionAndAnswerService {

    private final QuestionAndAnswerTxService questionAndAnswerTxService;

    public Stream<QuestionData.PromptWithAnswerWithChapterId> getQuestionToAsk(List<String> chapterIds, int limit) {

        Stream<QuestionData.PromptWithAnswerWithChapterId> stream = chapterIds.stream()
                .map(questionAndAnswerTxService::getQuestionToAsk)
                .flatMap(Collection::stream);
        if (limit!=0) {
            stream = stream.limit(limit);
        }
        return stream;
    }

/*
    private static Stream<QuestionWithAnswerToAsk> getStreamOfPrompt(Chapter chapter) {
        return chapter.getChapterParams().prompts.stream().map(QuestionWithAnswerToAsk::fromPrompt);

        if (chapterParams.kb.file!=null) {
            return Stream.empty();
        }
        else if (chapterParams.kb.git!=null) {
            QuestionData.Chapters qas = OpenaiJsonReader.read(chapter.id, mhbpHome, chapterParams.kb.git);
            return qas.list.stream();
        }
        else if (chapterParams.getKb().inline!=null) {
            return chapterParams.getKb().inline.stream()
                    .map(o->new QuestionWithAnswerToAsk(chapter.id, chapterParams.getKb().type, o.p, o.a));
        }
        throw new IllegalStateException();
    }
*/

    public void process(Session session, Chapter chapter, AnswerParams ap) {
        questionAndAnswerTxService.process(session, chapter, ap);
    }
}
