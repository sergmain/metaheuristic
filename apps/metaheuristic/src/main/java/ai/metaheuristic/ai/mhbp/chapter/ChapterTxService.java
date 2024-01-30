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

package ai.metaheuristic.ai.mhbp.chapter;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.mhbp.beans.Chapter;
import ai.metaheuristic.ai.mhbp.repositories.ChapterRepository;
import ai.metaheuristic.ai.mhbp.yaml.chapter.ChapterParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * @author Sergio Lissner
 * Date: 4/27/2023
 * Time: 3:08 AM
 */
@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class ChapterTxService {

    private final ChapterRepository chapterRepository;

    @Transactional
    public void saveSystemChapter(String kbCode, ChapterParams chapterParams) {
        Chapter chapter = new Chapter();
        chapter.code = kbCode;
        chapter.createdOn = System.currentTimeMillis();
        chapter.disabled = false;
        chapter.companyId = Consts.ID_1;
        // for companyId==1L it doesn't matter which accountId will be
        chapter.accountId = 0;
        chapter.updateParams(chapterParams);

        chapterRepository.save(chapter);
    }


}
