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

package ai.metaheuristic.ai.mhbp.chapter;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.dispatcher.DispatcherContext;
import ai.metaheuristic.ai.mhbp.beans.Kb;
import ai.metaheuristic.ai.mhbp.data.SimpleChapterUid;
import ai.metaheuristic.ai.mhbp.repositories.ChapterRepository;
import ai.metaheuristic.ai.mhbp.repositories.KbRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Sergio Lissner
 * Date: 4/28/2023
 * Time: 12:27 PM
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Profile("dispatcher")
public class ChapterService {

    public final KbRepository kbRepository;
    public final ChapterRepository chapterRepository;

    public List<Kb> getKbsAllowedForCompany(DispatcherContext context) {
        List<Kb> result = new ArrayList<>(50);
        List<Kb> kbs = kbRepository.findAllByCompanyUniqueId(Consts.ID_1);
        result.addAll(kbs);
        kbs = kbRepository.findAllByCompanyUniqueId(context.getCompanyId());
        result.addAll(kbs);
        return result;
    }

    public List<Long> getKbIdsAllowedForCompany(DispatcherContext context) {
        List<Long> result = new ArrayList<>(50);
        List<Long> kbs = kbRepository.findAllIdsByCompanyUniqueId(Consts.ID_1);
        result.addAll(kbs);
        kbs = kbRepository.findAllIdsByCompanyUniqueId(context.getCompanyId());
        result.addAll(kbs);
        return result;
    }

    public List<SimpleChapterUid> getChaptersAllowedForCompany(DispatcherContext context) {
        List<Long> kbIds = getKbIdsAllowedForCompany(context);
        List<SimpleChapterUid> result = chapterRepository.findAllByKbIds(kbIds);
        return result;
    }

}
