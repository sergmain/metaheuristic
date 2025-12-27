/*
 * Metaheuristic, Copyright (C) 2017-2025, Innovation platforms, LLC
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

import ai.metaheuristic.ai.dispatcher.batch.BatchTopLevelService;
import ai.metaheuristic.commons.utils.ZipUtils;
import org.junit.jupiter.api.Test;

import java.util.zip.ZipEntry;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Serge
 * Date: 6/5/2019
 * Time: 11:13 PM
 */
public class TestBatchZipProcessing {


    public static class CustomZipEntry extends ZipEntry {
        public CustomZipEntry(String name, long size) {
            super(name);
            super.setSize(size);
        }
    }

    @Test
    public void testZipName() {

        assertSame(ZipUtils.State.OK, BatchTopLevelService.VALIDATE_ZIP_FUNCTION.apply(new ZipEntry("aaa.txt")).state);
        assertSame(ZipUtils.State.OK, BatchTopLevelService.VALIDATE_ZIP_FUNCTION.apply(new ZipEntry("aaa.")).state);
        assertSame(ZipUtils.State.OK, BatchTopLevelService.VALIDATE_ZIP_FUNCTION.apply(new ZipEntry("bbb/aaa.")).state);
        assertSame(ZipUtils.State.OK, BatchTopLevelService.VALIDATE_ZIP_FUNCTION.apply(new ZipEntry("bbb/aaa")).state);

        assertSame(ZipUtils.State.ERROR, BatchTopLevelService.VALIDATE_ZIP_FUNCTION.apply(new ZipEntry("aaa bbb.txt")).state);
        assertSame(ZipUtils.State.ERROR, BatchTopLevelService.VALIDATE_ZIP_FUNCTION.apply(new ZipEntry("aaa,bbb.txt")).state);
        assertSame(ZipUtils.State.ERROR, BatchTopLevelService.VALIDATE_ZIP_FUNCTION.apply(new ZipEntry("aaaäöü.txt")).state);
    }

    @Test
    public void testFileSize() {
        assertEquals(ZipUtils.State.OK, BatchTopLevelService.VALIDATE_ZIP_ENTRY_SIZE_FUNCTION.apply(new CustomZipEntry("file1", 1L)).state);
        assertEquals(ZipUtils.State.ERROR, BatchTopLevelService.VALIDATE_ZIP_ENTRY_SIZE_FUNCTION.apply(new CustomZipEntry("file2", 0L)).state);
    }
}
