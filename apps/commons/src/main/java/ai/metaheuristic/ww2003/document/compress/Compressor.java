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

package ai.metaheuristic.ww2003.document.compress;

import ai.metaheuristic.ww2003.document.CDNode;
import ai.metaheuristic.ww2003.document.WW2003Document;
import ai.metaheuristic.ww2003.document.NodeFilters;
import ai.metaheuristic.ww2003.document.tags.xml.*;

import java.util.List;
import java.util.stream.Collectors;

public class Compressor {

    public static void compressRunTags(WW2003Document ww2003Document) {
        ww2003Document.asStream(CDNode.class, new NodeFilters.InstanceFilter(Para.class)).forEach(para -> {
            List<Run> runs = para.asStream(Run.class).collect(Collectors.toList());
            if (runs.size() < 2) {
                return;
            }
            for (int i = 0; i < runs.size() - 1; i++) {
                while ((runs.size() - i) > 1 && hasProperties(runs.get(i), runs.get(i + 1))) {
                    runs.get(i).getLast(Text.class).concat(runs.get(i + 1).getText());
                    runs.get(i + 1).removeFromParent();
                    runs.remove(i + 1);
                }
            }
        });
    }

    private static boolean hasProperties(Run run1, Run run2) {

        return run1.hasProperty(RProp.class, Vanish.class) && run2.hasProperty(RProp.class, Vanish.class) ||
                run1.hasProperty(RProp.class, U.class) && run2.hasProperty(RProp.class, U.class);
    }

}
