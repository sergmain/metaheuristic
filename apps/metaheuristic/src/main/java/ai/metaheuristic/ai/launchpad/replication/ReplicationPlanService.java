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

import ai.metaheuristic.ai.launchpad.beans.PlanImpl;
import ai.metaheuristic.ai.launchpad.data.ReplicationData;
import ai.metaheuristic.ai.launchpad.plan.PlanCache;
import ai.metaheuristic.ai.launchpad.repositories.PlanRepository;
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
    public final PlanRepository planRepository;
    public final PlanCache planCache;

    @Data
    @AllArgsConstructor
    private static class PlanLoopEntry {
        public ReplicationData.PlanShortAsset planShort;
        public PlanImpl plan;
    }

    public void syncPlans(List<ReplicationData.PlanShortAsset> actualPlans) {
        List<PlanLoopEntry> forUpdating = new ArrayList<>(actualPlans.size());
        LinkedList<ReplicationData.PlanShortAsset> forCreating = new LinkedList<>(actualPlans);

        List<Long> ids = planRepository.findAllAsIds();
        for (Long id : ids) {
            PlanImpl p = planCache.findById(id);
            if (p==null) {
                continue;
            }

            boolean isDeleted = true;
            for (ReplicationData.PlanShortAsset actualPlan : actualPlans) {
                if (actualPlan.code.equals(p.code)) {
                    isDeleted = false;
                    if (actualPlan.updateOn != p.getPlanParamsYaml().internalParams.updatedOn) {
                        PlanLoopEntry planLoopEntry = new PlanLoopEntry(actualPlan, p);
                        forUpdating.add(planLoopEntry);
                    }
                    break;
                }
            }

            if (isDeleted) {
                planCache.deleteById(id);
            }
            forCreating.removeIf(planShortAsset -> planShortAsset.code.equals(p.code));
        }

        forUpdating.parallelStream().forEach(this::updatePlan);
        forCreating.parallelStream().forEach(this::createPlan);
    }

    private void updatePlan(PlanLoopEntry planLoopEntry) {
        ReplicationData.PlanAsset planAsset = getPlanAsset(planLoopEntry.plan.code);
        if (planAsset == null) {
            return;
        }

        planLoopEntry.plan.setParams( planAsset.plan.getParams() );
        planLoopEntry.plan.locked = planAsset.plan.locked;
        planLoopEntry.plan.valid = planAsset.plan.valid;

        planCache.save(planLoopEntry.plan);
    }

    private void createPlan(ReplicationData.PlanShortAsset planShortAsset) {
        ReplicationData.PlanAsset planAsset = getPlanAsset(planShortAsset.code);
        if (planAsset == null) {
            return;
        }

        PlanImpl p = planRepository.findByCode(planShortAsset.code);
        if (p!=null) {
            return;
        }

        planAsset.plan.id=null;
        planCache.save(planAsset.plan);
    }

    private ReplicationData.PlanAsset getPlanAsset(String planCode) {
        ReplicationData.PlanAsset planAsset = requestPlanAsset(planCode);
        if (planAsset.isErrorMessages()) {
            log.error("#308.020 Error while getting plan "+ planCode +", error: " + planAsset.getErrorMessagesAsStr());
            return null;
        }
        return planAsset;
    }

    private ReplicationData.PlanAsset requestPlanAsset(String planCode) {
        Object data = replicationCoreService.getData(
                "/rest/v1/replication/plan", ReplicationData.PlanAsset.class,
                (uri) -> Request.Post(uri)
                        .bodyForm(Form.form().add("planCode", planCode).build(), StandardCharsets.UTF_8)
                        .connectTimeout(5000)
                        .socketTimeout(20000)
        );
        if (data instanceof ReplicationData.AssetAcquiringError) {
            return new ReplicationData.PlanAsset(((ReplicationData.AssetAcquiringError) data).errorMessages);
        }
        ReplicationData.PlanAsset response = (ReplicationData.PlanAsset) data;
        return response;
    }

}