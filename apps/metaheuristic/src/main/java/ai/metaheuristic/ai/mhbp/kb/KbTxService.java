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

package ai.metaheuristic.ai.mhbp.kb;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.dispatcher.DispatcherContext;
import ai.metaheuristic.ai.mhbp.beans.Chapter;
import ai.metaheuristic.ai.mhbp.beans.Kb;
import ai.metaheuristic.ai.mhbp.beans.Part;
import ai.metaheuristic.ai.mhbp.questions.QuestionData;
import ai.metaheuristic.ai.mhbp.repositories.ChapterRepository;
import ai.metaheuristic.ai.mhbp.repositories.KbRepository;
import ai.metaheuristic.ai.mhbp.yaml.chapter.ChapterParams;
import ai.metaheuristic.ai.mhbp.yaml.kb.KbParams;
import ai.metaheuristic.ai.mhbp.yaml.part.PartParams;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.OperationStatusRest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.springframework.transaction.annotation.Propagation.SUPPORTS;

/**
 * @author Sergio Lissner
 * Date: 4/15/2023
 * Time: 2:34 PM
 */
@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class KbTxService {

    private final KbRepository kbRepository;
    private final ChapterRepository chapterRepository;

/*
    private Path gitPath;

    @PostConstruct
    public void postConstruct() {
        gitPath = globals.getHome().resolve("git");
    }
*/

    @Transactional(propagation= SUPPORTS)
    public List<Kb> findSystemKbs() {
        return kbRepository.findAllByCompanyUniqueId(Consts.ID_1);
    }

    @Transactional
    public void saveSystemKb(KbParams kbParams) {
        Kb kb = new Kb();
        kb.code = kbParams.kb.code;
        kb.createdOn = System.currentTimeMillis();
        kb.disabled = kbParams.disabled;
        kb.companyId = Consts.ID_1;
        // for companyId==1L it doesn't matter which accountId will be
        kb.accountId = 0;
        kb.updateParams(kbParams);

        kbRepository.save(kb);
    }

    @Transactional
    public void updateDisabled(Long kbId, boolean disabled) {
        Kb kb = kbRepository.findById(kbId).orElse(null);
        if (kb==null) {
            return;
        }
        kb.disabled = disabled;
        kbRepository.save(kb);
    }

    @Transactional
    public OperationStatusRest deleteKbById(Long kbId, DispatcherContext context) {
        if (kbId==null) {
            return OperationStatusRest.OPERATION_STATUS_OK;
        }
        Kb kb = kbRepository.findById(kbId).orElse(null);
        if (kb == null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "209.040 KB wasn't found, kbId: " + kbId);
        }
        if (kb.companyId!=context.getCompanyId()) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "209.080 kbId: " + kbId);
        }

        kbRepository.delete(kb);
        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    @Transactional
    public OperationStatusRest createKb(String code, String params, DispatcherContext context) {
        Kb kb = new Kb();
        kb.code = code;
        kb.status = Enums.KbStatus.none.code;
        kb.setParams(params);
        kb.companyId = context.getCompanyId();
        kb.accountId = context.getAccountId();
        kb.createdOn = System.currentTimeMillis();

        kbRepository.save(kb);

        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    @Transactional
    public void markAsReady(long kbId) {
        Kb kb = kbRepository.findById(kbId).orElse(null);
        if (kb == null) {
            return;
        }
        if (kb.status==Enums.KbStatus.ready.code) {
            return;
        }
        kb.status = Enums.KbStatus.ready.code;
        kbRepository.save(kb);
    }

    @Transactional
    public Chapter storePrompts(QuestionData.ChapterWithPrompts chapter, List<PartParams> partParams, long kbId, long companyId, long accountId) {
        Chapter c = chapterRepository.findByKbIdAndCode(kbId, chapter.chapterCode());
        if (c==null) {
            c = new Chapter();
            c.kbId = kbId;
            c.code = chapter.chapterCode();
            c.companyId = companyId;
            c.accountId = accountId;
            c.createdOn = System.currentTimeMillis();
            c.disabled = false;
            // for what is this status?
            c.status = 0;
        }
        c.promptCount = partParams.stream().mapToInt(o->o.prompts.size()).sum();
        c.updateParams(new ChapterParams());

        if (c.getParts().size() > partParams.size()) {
            c.getParts().subList(partParams.size(), c.getParts().size()).clear();
        }
        for (int i = c.getParts().size(); i < partParams.size(); i++) {
            c.getParts().add(new Part());
        }
        for (int i = 0; i < partParams.size(); i++) {
            c.getParts().get(i).updateParams(partParams.get(i));
        }

        return chapterRepository.save(c);
    }

}
