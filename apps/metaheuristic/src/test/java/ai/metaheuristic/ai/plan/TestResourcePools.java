/*
 * Metaheuristic, Copyright (C) 2017-2019  Serge Maslyukov
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

package ai.metaheuristic.ai.plan;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.launchpad.plan.PlanService;
import ai.metaheuristic.api.v1.data_storage.DataStorageParams;
import ai.metaheuristic.ai.yaml.data_storage.DataStorageParamsUtils;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

public class TestResourcePools {

    private static final DataStorageParams SOURCING_DISK_PARAMS = DataStorageParamsUtils.to("sourcing: disk");

    @Test
    public void testResourcePools() {
        PlanService.ResourcePools p = new PlanService.ResourcePools();
        new ArrayList<>();
        p.collectedInputs.put("aaa", new ArrayList<>(List.of("a1", "a2", "a3")));
        p.inputStorageUrls = new HashMap<>();
        p.inputStorageUrls.put("aaa", Consts.SOURCING_LAUNCHPAD_PARAMS);

        PlanService.ResourcePools p1 = new PlanService.ResourcePools();
        p1.collectedInputs.put("aaa", new ArrayList<>(List.of("a4")));
        p1.collectedInputs.put("bbb", new ArrayList<>(List.of("b1", "b2", "b3")));
        p1.inputStorageUrls = new HashMap<>();
        p1.inputStorageUrls.put("bbb", SOURCING_DISK_PARAMS);

        p.merge(p1);

        assertEquals(2, p.collectedInputs.keySet().size());
        assertEquals(4, p.collectedInputs.get("aaa").size());
        assertTrue(p.collectedInputs.get("aaa").contains("a1"));
        assertTrue(p.collectedInputs.get("aaa").contains("a2"));
        assertTrue(p.collectedInputs.get("aaa").contains("a3"));
        assertTrue(p.collectedInputs.get("aaa").contains("a4"));

        assertEquals(3, p.collectedInputs.get("bbb").size());
        assertTrue(p.collectedInputs.get("bbb").contains("b1"));
        assertTrue(p.collectedInputs.get("bbb").contains("b2"));
        assertTrue(p.collectedInputs.get("bbb").contains("b3"));

        assertEquals(2, p.inputStorageUrls.keySet().size());
        DataStorageParams params = p.inputStorageUrls.get("aaa");
        assertEquals(Consts.SOURCING_LAUNCHPAD_PARAMS.sourcing, params.sourcing);

        params = p.inputStorageUrls.get("bbb");
        assertEquals(SOURCING_DISK_PARAMS.sourcing, params.sourcing);
    }
}
