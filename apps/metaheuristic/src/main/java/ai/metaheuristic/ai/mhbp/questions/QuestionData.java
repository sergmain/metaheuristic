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

package ai.metaheuristic.ai.mhbp.questions;

import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.mhbp.yaml.part.PartParams;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Sergio Lissner
 * Date: 3/19/2023
 * Time: 11:01 PM
 */
public class QuestionData {

    public record PromptWithAnswerWithChapterId(long chapterId, long partId, QuestionWithAnswerToAsk prompt) {
        public static PromptWithAnswerWithChapterId fromPrompt(long chapterId, long partId, PartParams.Prompt prompt) {
            return new PromptWithAnswerWithChapterId(chapterId, partId, new QuestionWithAnswerToAsk(prompt.p, prompt.a));
        }
    }

    public record QuestionWithAnswerToAsk(String q, String a) {
        public PartParams.Prompt toPrompt() {
            return new PartParams.Prompt(q, a);
        }
    }

    public record ChapterWithPrompts(String chapterCode, List<QuestionWithAnswerToAsk> list) {}

    @NoArgsConstructor
    @AllArgsConstructor
    public static class Chapters {
        public List<ChapterWithPrompts> chapters = new ArrayList<>();
        public long kbId;
        public Enums.KbSourceInitStatus initStatus;

        public Chapters(Enums.KbSourceInitStatus initStatus) {
            this.initStatus = initStatus;
        }

        public Chapters(long kbId) {
            this.kbId = kbId;
        }
    }
}
