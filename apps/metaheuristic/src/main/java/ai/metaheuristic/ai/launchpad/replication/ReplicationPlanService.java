/*
 * Metaheuristic, Copyright (C) 2017-2020  Serge Maslyukov
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

package ai.metaheuristic.ai.launchpad.replication;

import ai.metaheuristic.ai.launchpad.beans.SourceCodeImpl;
import ai.metaheuristic.ai.launchpad.data.ReplicationData;
import ai.metaheuristic.ai.launchpad.source_code.SourceCodeCache;
import ai.metaheuristic.ai.launchpad.repositories.SourceCodeRepository;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.fluent.Form;
import org.apache.http.client.fluent.Request;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * @author Serge
 * Date: 1/13/2020
 * Time: 7:10 PM
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Profile("launchpad")
public class ReplicationPlanService {

    public final ReplicationCoreService replicationCoreService;
    public final SourceCodeRepository sourceCodeRepository;
    public final SourceCodeCache sourceCodeCache;

    @Data
    @AllArgsConstructor
    private static class PlanLoopEntry {
        public ReplicationData.SourceCodeShortAsset planShort;
        public SourceCodeImpl plan;
    }

    public void syncPlans(List<ReplicationData.SourceCodeShortAsset> actualPlans) {
        List<PlanLoopEntry> forUpdating = new ArrayList<>(actualPlans.size());
        LinkedList<ReplicationData.SourceCodeShortAsset> forCreating = new LinkedList<>(actualPlans);

        List<Long> ids = sourceCodeRepository.findAllAsIds();
        for (Long id : ids) {
            SourceCodeImpl p = sourceCodeCache.findById(id);
            if (p==null) {
                continue;
            }

            boolean isDeleted = true;
            for (ReplicationData.SourceCodeShortAsset actualPlan : actualPlans) {
                if (actualPlan.uid.equals(p.uid)) {
                    isDeleted = false;
                    if (actualPlan.updateOn != p.getSourceCodeParamsYaml().internalParams.updatedOn) {
                        PlanLoopEntry planLoopEntry = new PlanLoopEntry(actualPlan, p);
                        forUpdating.add(planLoopEntry);
                    }
                    break;
                }
            }

            if (isDeleted) {
                sourceCodeCache.deleteById(id);
            }
            forCreating.removeIf(sourceCodeShortAsset -> sourceCodeShortAsset.uid.equals(p.uid));
        }

        forUpdating.parallelStream().forEach(this::updatePlan);
        forCreating.parallelStream().forEach(this::createPlan);
    }

    private void updatePlan(PlanLoopEntry planLoopEntry) {
        ReplicationData.SourceCodeAsset sourceCodeAsset = getPlanAsset(planLoopEntry.plan.uid);
        if (sourceCodeAsset == null) {
            return;
        }

        planLoopEntry.plan.setParams( sourceCodeAsset.sourceCode.getParams() );
        planLoopEntry.plan.locked = sourceCodeAsset.sourceCode.locked;
        planLoopEntry.plan.valid = sourceCodeAsset.sourceCode.valid;

        sourceCodeCache.save(planLoopEntry.plan);
    }

    private void createPlan(ReplicationData.SourceCodeShortAsset sourceCodeShortAsset) {
        ReplicationData.SourceCodeAsset sourceCodeAsset = getPlanAsset(sourceCodeShortAsset.uid);
        if (sourceCodeAsset == null) {
            return;
        }

        SourceCodeImpl p = sourceCodeRepository.findByCode(sourceCodeShortAsset.uid);
        if (p!=null) {
            return;
        }

        sourceCodeAsset.sourceCode.id=null;
        sourceCodeCache.save(sourceCodeAsset.sourceCode);
    }

    private ReplicationData.SourceCodeAsset getPlanAsset(String planCode) {
        ReplicationData.SourceCodeAsset sourceCodeAsset = requestPlanAsset(planCode);
        if (sourceCodeAsset.isErrorMessages()) {
            log.error("#308.020 Error while getting sourceCode "+ planCode +", error: " + sourceCodeAsset.getErrorMessagesAsStr());
            return null;
        }
        return sourceCodeAsset;
    }

    private ReplicationData.SourceCodeAsset requestPlanAsset(String planCode) {
        Object data = replicationCoreService.getData(
                "/rest/v1/replication/sourceCode", ReplicationData.SourceCodeAsset.class,
                (uri) -> Request.Post(uri)
                        .bodyForm(Form.form().add("planCode", planCode).build(), StandardCharsets.UTF_8)
                        .connectTimeout(5000)
                        .socketTimeout(20000)
        );
        if (data instanceof ReplicationData.AssetAcquiringError) {
            return new ReplicationData.SourceCodeAsset(((ReplicationData.AssetAcquiringError) data).errorMessages);
        }
        ReplicationData.SourceCodeAsset response = (ReplicationData.SourceCodeAsset) data;
        return response;
    }

}