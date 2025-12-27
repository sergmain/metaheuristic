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

package ai.metaheuristic.ai.processor.actors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Sergio Lissner
 * Date: 10/19/2023
 * Time: 9:37 PM
 */
public class DownloadUtilsTest {


    @Test
    public void test_combineParts(@TempDir Path temp) throws IOException {
        String s1 = "part1\n";
        String s2 = "part2\n";
        Path p1 = temp.resolve("s.txt.0.tmp");
        Files.writeString(p1, s1);

        Path p2 = temp.resolve("s.txt.1.tmp");
        Files.writeString(p2, s2);

        Path r = temp.resolve("s-result.txt");


        DownloadUtils.combineParts( temp.resolve("s.txt"), r, 1);


        String s = Files.readString(r);
        assertEquals(s1+s2, s);

    }
}
