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
import ai.metaheuristic.ai.core.SystemProcessLauncher;
import ai.metaheuristic.ai.dispatcher.DispatcherContext;
import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.mhbp.kb.reader.openai.OpenaiJsonReader;
import ai.metaheuristic.ai.mhbp.repositories.KbRepository;
import ai.metaheuristic.ai.utils.JsonUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.OperationStatusRest;
import ai.metaheuristic.ai.mhbp.beans.Chapter;
import ai.metaheuristic.ai.mhbp.beans.Kb;
import ai.metaheuristic.ai.mhbp.data.KbData;
import ai.metaheuristic.ai.mhbp.events.InitKbEvent;
import ai.metaheuristic.ai.mhbp.questions.QuestionData;
import ai.metaheuristic.ai.mhbp.services.LocalGitRepoService;
import ai.metaheuristic.ai.mhbp.yaml.kb.KbParams;
import ai.metaheuristic.ai.mhbp.yaml.part.PartParams;
import ai.metaheuristic.ai.mhbp.yaml.part.PartParamsUtils;
import ai.metaheuristic.commons.utils.PageUtils;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static ai.metaheuristic.ai.mhbp.questions.QuestionData.Chapters;

/**
 * @author Sergio Lissner
 * Date: 4/15/2023
 * Time: 2:34 PM
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Profile("dispatcher")
public class KbService {

    private final Globals globals;
    private final KbTxService kbTxService;
    private final KbRepository kbRepository;
    private final LocalGitRepoService gitRepoService;
    private final ApplicationEventPublisher eventPublisher;

    @PostConstruct
    public void postConstruct() {
        if (globals.mhbp.kb!=null) {
            List<Kb> kbs = kbTxService.findSystemKbs();
            for (Globals.Kb globalKb : globals.mhbp.kb) {
                boolean create = true;
                for (Kb kb : kbs) {
                    if (kb.code.equals(globalKb.code)) {
                        create = false;
                        if (kb.disabled!=globalKb.disabled) {
                            kbTxService.updateDisabled(kb.id, globalKb.disabled);
                        }
                        break;
                    }
                }
                if (create) {
                    KbParams kbParams = toKbParams(globalKb);
                    kbTxService.saveSystemKb(kbParams);
                }
            }
        }

        int i=0;
    }

    public static KbParams toKbParams(@NonNull Globals.Kb v1) {
        KbParams t = new KbParams();
        t.disabled = v1.disabled;
        t.kb.code = v1.code;
        t.kb.type = Enums.KbFileFormat.valueOf(v1.type);
        t.kb.git = toGit(v1.git);
        t.kb.file = toFile(v1.file);

        t.checkIntegrity();
        return t;
    }

    @Nullable
    private static KbParams.File toFile(@Nullable Globals.KbFile v1) {
        if (v1==null) {
            return null;
        }
        KbParams.File f = new KbParams.File(v1.url);
        return f;
    }

    @Nullable
    private static KbParams.Git toGit(@Nullable Globals.Git v1) {
        if (v1==null) {
            return null;
        }
        KbParams.Git g = new KbParams.Git(v1.repo, v1.branch, v1.commit);
        v1.kbPaths.stream().map(KbService::toKbPath).collect(Collectors.toCollection(()->g.kbPaths));
        return g;
    }

    @Nullable
    public static KbParams.KbPath toKbPath(Globals.KbPath v1) {
        KbParams.KbPath ta = new KbParams.KbPath(v1.evals, v1.data);
        return ta;
    }

    public KbData.Kbs getKbs(Pageable pageable, DispatcherContext context) {
        pageable = PageUtils.fixPageSize(20, pageable);

        List<KbData.SimpleKb> simpleKbs = new ArrayList<>(50);
        Page<Kb> kbs = kbRepository.findAllByCompanyUniqueId(pageable, Consts.ID_1);
        kbs.stream().map(KbData.SimpleKb::editableSimpleKb).collect(Collectors.toCollection(()->simpleKbs));

        kbs = kbRepository.findAllByCompanyUniqueId(pageable, context.getCompanyId());
        kbs.stream().map(KbData.SimpleKb::editableSimpleKb).collect(Collectors.toCollection(()->simpleKbs));

        var sorted = simpleKbs.stream().sorted((o1, o2)->Long.compare(o2.id, o1.id)).collect(Collectors.toList());
        return new KbData.Kbs(new PageImpl<>(sorted, pageable, simpleKbs.size()));
    }

    public KbData.Kb getKb(@Nullable Long kbId, DispatcherContext context) {
        if (kbId==null) {
            return new KbData.Kb("261.040 Not found");
        }
        Kb kb = kbRepository.findById(kbId).orElse(null);
        if (kb == null) {
            return new KbData.Kb("261.080 Not found");
        }
        if (kb.companyId!=context.getCompanyId()) {
            return new KbData.Kb("261.120 Illegal access");
        }
        return new KbData.Kb(new KbData.SimpleKb(kb));
    }

    public OperationStatusRest initKb(Long kbId, DispatcherContext context) {
        if (kbId==null) {
            return OperationStatusRest.OPERATION_STATUS_OK;
        }
        Kb kb = kbRepository.findById(kbId).orElse(null);
        if (kb == null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "261.160  KB wasn't found, kbId: " + kbId);
        }
/*
        if (kb.companyId!=context.getCompanyId()) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "261.200 Access denied, kbId: " + kbId);
        }
*/
        eventPublisher.publishEvent(new InitKbEvent(kbId, kb.companyId, context.getAccountId()));
        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    @SneakyThrows
    public void processInitKbEvent(InitKbEvent event) {
        Kb kb = kbRepository.findById(event.kbId()).orElse(null);
//        if (kb == null || kb.status==Enums.KbStatus.ready.code) {
        if (kb == null) {
            return;
        }
        KbParams kbParams = kb.getKbParams();
        if (kbParams.kb.git!=null) {
            final SystemProcessLauncher.ExecResult execResult = gitRepoService.initGitRepo(kbParams.kb.git);
            if (execResult.ok) {
                if (execResult.functionDir==null) {
                    throw new IllegalStateException();
                }
                final String asString = JsonUtils.getMapper().writeValueAsString(execResult);
                Chapters chapters = OpenaiJsonReader.read(kb.id, execResult.functionDir, kbParams.kb.git);
                storePrompts(chapters, event.companyId(), event.accountId());
            }
        }
        if (kbParams.kb.file!=null) {

        }

        kbTxService.markAsReady(event.kbId());

    }

    public void storePrompts(Chapters chapters, long companyId, long accountId) {
        log.info("Status of chapters: " + chapters.initStatus);
        if (chapters.initStatus!=Enums.KbSourceInitStatus.ready) {
            return;
        }

        long mills = System.currentTimeMillis();
        log.info("Start saving chapters");

        int total = 0;
        for (QuestionData.ChapterWithPrompts chapter : chapters.chapters) {
            List<PartParams> partParams = new ArrayList<>();
            PartParams curr = null;
            for (QuestionData.QuestionWithAnswerToAsk questionWithAnswerToAsk : chapter.list()) {
                if (curr==null) {
                    curr = new PartParams();
                    partParams.add(curr);
                }
                curr.prompts.add(questionWithAnswerToAsk.toPrompt());
                if (curr.prompts.size()>=globals.mhbp.max.promptsPerPart) {
                    curr = null;
                }
            }

            if (log.isInfoEnabled()) {
                log.info("chapter {}", chapter.chapterCode());
                for (PartParams partParam : partParams) {
                    String s = PartParamsUtils.UTILS.toString(partParam);
                    log.info("'\tprompts:{}, len: {}", partParam.prompts.size(), s.length());
                }
            }
            Chapter c = kbTxService.storePrompts(chapter, partParams, chapters.kbId, companyId, accountId);
            log.info("'\tchapter {} is stored, prompts: {}", chapter.chapterCode(), c.promptCount);
            total += c.promptCount;
        }
        log.info("All chapters are saved in {} seconds. Total prompts: {}", (System.currentTimeMillis()-mills)/1000, total);
    }


}
