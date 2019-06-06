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

package ai.metaheuristic.ai.batch;

import ai.metaheuristic.ai.batch.process_resource.ProcessResourceController;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author Serge
 * Date: 6/5/2019
 * Time: 11:13 PM
 */
public class TestBatchZipProcessing {

    @Test
    public void testZipName() {
        assertTrue(ProcessResourceController.isZipEntityNameOk("aaa.txt"));
        assertTrue(ProcessResourceController.isZipEntityNameOk("aaa."));
        assertTrue(ProcessResourceController.isZipEntityNameOk("bbb/aaa."));
        assertTrue(ProcessResourceController.isZipEntityNameOk("bbb/aaa"));

        assertFalse(ProcessResourceController.isZipEntityNameOk("aaa bbb.txt"));
        assertFalse(ProcessResourceController.isZipEntityNameOk("aaa,bbb.txt"));
        assertFalse(ProcessResourceController.isZipEntityNameOk("aaaäöü.txt"));
    }
}
