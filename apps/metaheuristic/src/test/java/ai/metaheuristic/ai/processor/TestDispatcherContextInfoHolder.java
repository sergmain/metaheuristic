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

package ai.metaheuristic.ai.processor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * @author Serge
 * Date: 1/2/2021
 * Time: 9:57 AM
 */
public class TestDispatcherContextInfoHolder {

    @Test
    public void test() {
        ProcessorAndCoreData.AssetManagerUrl assetManagerUrl = new ProcessorAndCoreData.AssetManagerUrl("asset-url");
        DispatcherContextInfoHolder.put(assetManagerUrl, 42L);

        assertNotNull(DispatcherContextInfoHolder.getCtx(assetManagerUrl));
        // ###IDEA###, why?
        assertEquals(42L, DispatcherContextInfoHolder.getCtx(assetManagerUrl).chunkSize);

        ProcessorAndCoreData.DispatcherUrl dispatcherUrl = new ProcessorAndCoreData.DispatcherUrl("dispatcher-url");
        DispatcherContextInfoHolder.put(dispatcherUrl, 13L);

        assertNotNull(DispatcherContextInfoHolder.getCtx(dispatcherUrl));
        // ###IDEA###, why?
        assertEquals(13L, DispatcherContextInfoHolder.getCtx(dispatcherUrl).chunkSize);


        ProcessorAndCoreData.DispatcherUrl dispatcherUrl1 = new ProcessorAndCoreData.DispatcherUrl("common-url");
        DispatcherContextInfoHolder.put(dispatcherUrl1, 21L);

        assertNotNull(DispatcherContextInfoHolder.getCtx(dispatcherUrl1));
        // ###IDEA###, why?
        assertEquals(21L, DispatcherContextInfoHolder.getCtx(dispatcherUrl1).chunkSize);

        ProcessorAndCoreData.AssetManagerUrl assetManagerUrl1 = new ProcessorAndCoreData.AssetManagerUrl("common-url");
        assertNotNull(DispatcherContextInfoHolder.getCtx(assetManagerUrl1));
        // ###IDEA###, why?
        assertEquals(21L, DispatcherContextInfoHolder.getCtx(assetManagerUrl1).chunkSize);

        DispatcherContextInfoHolder.put(assetManagerUrl1, 17L);
        assertNotNull(DispatcherContextInfoHolder.getCtx(assetManagerUrl1));
        // ###IDEA###, why?
        assertEquals(17, DispatcherContextInfoHolder.getCtx(assetManagerUrl1).chunkSize);


    }
}
