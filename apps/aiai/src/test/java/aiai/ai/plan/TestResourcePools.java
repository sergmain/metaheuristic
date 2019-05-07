/*
 * AiAi, Copyright (C) 2017-2019  Serge Maslyukov
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package aiai.ai.plan;

import aiai.ai.launchpad.plan.PlanService;
import org.junit.Assert;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

public class TestResourcePools {

    @Test
    public void testResourcePools() {
        PlanService.ResourcePools p = new PlanService.ResourcePools();
        new ArrayList<>();
        p.collectedInputs.put("aaa", new ArrayList<>(List.of("a1", "a2", "a3")));
        p.inputStorageUrls = new HashMap<>();
        p.inputStorageUrls.put("aaa", "zzz");

        PlanService.ResourcePools p1 = new PlanService.ResourcePools();
        p1.collectedInputs.put("aaa", new ArrayList<>(List.of("a4")));
        p1.collectedInputs.put("bbb", new ArrayList<>(List.of("b1", "b2", "b3")));
        p1.inputStorageUrls = new HashMap<>();
        p1.inputStorageUrls.put("bbb", "yyy");

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
        assertEquals("zzz", p.inputStorageUrls.get("aaa"));
        assertEquals("yyy", p.inputStorageUrls.get("bbb"));
    }
}
