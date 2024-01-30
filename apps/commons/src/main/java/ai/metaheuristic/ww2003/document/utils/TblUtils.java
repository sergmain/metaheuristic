/*
 * Metaheuristic, Copyright (C) 2017-2024, Innovation platforms, LLC
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

package ai.metaheuristic.ww2003.document.utils;

import ai.metaheuristic.ww2003.document.tags.xml.table.*;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * @author Serge
 * Date: 4/26/2022
 * Time: 10:44 PM
 */
public class TblUtils {

    public static Tbl.MetaInfo getMetaInfo(Tbl tbl) {

        Tbl.MetaInfo metaInfo = new Tbl.MetaInfo();

        List<TRow> rows = tbl.asStream(TRow.class).collect(Collectors.toList());
        for (int i = 0; i < rows.size(); i++) {
            TRow row = rows.get(i);
            Optional<TCell> optionalTCell = row.asStream(TCell.class).findFirst();
            if (optionalTCell.isPresent()) {
                if (optionalTCell.get().getText().equals("1")) {
                    List<TCell> cells = row.asStream(TCell.class).collect(Collectors.toList());
                    boolean found = true;
                    for (int j = 0; j < cells.size(); j++) {
                        TCell cell = cells.get(j);
                        if (!cell.getText().equals(String.valueOf(j + 1))) {
                            found = false;
                            break;
                        }
                    }
                    metaInfo.columnNumbersRow = i + 1;
                    metaInfo.columnNumbers = IntStream.range(1, cells.size() + 1).mapToObj(String::valueOf).collect(Collectors.joining(";"));
                    if (found) {
                        break;
                    }
                }
            }
        }
        metaInfo.tableProp = tbl.findProperty(TblProp.class).orElse(null);
        metaInfo.tableGrid = tbl.asStream(TblGrid.class).findFirst().orElse(null);
        return metaInfo;
    }
}
