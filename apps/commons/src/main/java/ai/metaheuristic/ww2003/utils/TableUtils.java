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

package ai.metaheuristic.ww2003.utils;

import ai.metaheuristic.ww2003.document.*;
import ai.metaheuristic.ww2003.document.exceptions.UtilsExecutingException;
import ai.metaheuristic.ww2003.document.tags.xml.Attr;
import ai.metaheuristic.ww2003.document.tags.xml.table.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@SuppressWarnings("SimplifyStreamApiCallChains")
public class TableUtils {

    public static List<TRow> getMergedRows(TRow row) {
        List<TRow> rows = new ArrayList<>();
        rows.add(row);
        row.findFirst(TCell.class).ifPresent(tCell -> {
            if (tCell.hasProperty(TCellProp.class, VMerge.class, "val", "restart")) {
                CDNode nextRow = row.hasNext() ? row.getNext() : null;
                while (nextRow != null && nextRow.instanceOfTRow()) {
                    if (checkFirstCellMerged(nextRow)) {
                        rows.add(nextRow.asTRow());
                    } else {
                        break;
                    }
                    nextRow = nextRow.hasNext() ? nextRow.getNext() : null;
                }
            }
        });
        return rows;
    }

    public static List<List<TRow>> getAllMergedRows(Tbl table) {
        CDNode tRow = getFirstTRow(table);
        List<List<TRow>> mergedRow = new ArrayList<>();
        while (tRow != null && tRow.instanceOfTRow()) {
            List<TRow> rows = TableUtils.getMergedRows(tRow.asTRow());
            mergedRow.add(rows);
            final TRow row = rows.get(rows.size() - 1);
            tRow = row.hasNext() ? row.getNext() : null;
        }
        return mergedRow;
    }

    private static TRow getFirstTRow(Tbl table) {
        return table.findFirst(TRow.class).orElseThrow(() -> {
            String cdNodes = table.asStream(Composite.class, new NodeFilters.CountFilter(10)).map(o -> o.getClass().getSimpleName()).collect(Collectors.joining(", "));
            return new UtilsExecutingException("759.100 TRow wasn't found in table, actual 10 nodes: " + cdNodes);
        });
    }

    public static List<TRow> getTableHeader(Tbl table) {
        TRow headerRow = getFirstTRow(table);
        return getMergedRows(headerRow);
    }

    private static boolean checkFirstCellMerged(CDNode row) {
        return row.asTRow().findFirst(TCell.class)
                .map(tCell -> {
                    if (tCell.hasProperty(TCellProp.class, VMerge.class, "val", "restart")) {
                        return false;
                    }
                    return tCell.findProperty(TCellProp.class, VMerge.class).isPresent();
                })
                .orElse(false);
    }

    public static int getGridSpanValue(TCell node) {
        return node.findProperty(TCellProp.class, GridSpan.class).map(
                gridSpan -> gridSpan
                        .findAttributeByName("val")
                        .map(attr -> Integer.parseInt(attr.value))
                        .filter(value -> value > 1)
                        .orElse(0)
        ).orElse(0);
    }

    public static void setGridSpanValue(TCell cell, int gsVal) {
        if (gsVal > 1) {
            cell.findProperty(TCellProp.class).ifPresentOrElse(
                    tCellProp -> tCellProp.findFirst(GridSpan.class).ifPresentOrElse(
                            gridSpan -> changeGridSpan(gridSpan, gsVal),
                            () -> createGridSpan(tCellProp, gsVal)),
                    () -> createCellProp(cell, gsVal)
            );
        }
    }

    public static TCell setGridSpanValueAndGet(TCell node, int gsVal) {
        setGridSpanValue(node, gsVal);
        return node;
    }

    private static void createCellProp(TCell tCell, int gsVal) {
        final TCellProp tCellProp = new TCellProp();
        createGridSpan(tCellProp, gsVal);
        tCell.setProperty(tCellProp);
    }

    private static void changeGridSpan(GridSpan gridSpan, int gsVal) {
        gridSpan.findAttributeByName("val").ifPresentOrElse(
                attr -> gridSpan.replaceAttribute(attr, Attr.get(attr.nameSpace, attr.name, String.valueOf(gsVal))),
                () -> gridSpan.addAttribute(Attr.get("w", "val", String.valueOf(gsVal))));
    }

    private static void createGridSpan(TCellProp tCellProp, int gsVal) {
        tCellProp.add(new GridSpan(Attr.get("w", "val", String.valueOf(gsVal))));
    }

    public static TCellProp createVMergeStart() {
        return new TCellProp(new VMerge(Attr.get("w", "val", "restart")));
    }

    public static TCellProp createVMergeContinue() {
        return new TCellProp(new VMerge(Attr.get("w", "val", "continue")));
    }

    public static TRow createMergedRow(int... cellCounts) {
        TRow row = new TRow();
        for (int count : cellCounts) {
            row.add(setGridSpanValueAndGet(new TCell(), count));
        }
        return row;
    }

    public static void setTransparentSeparator(TRow row) {
        row.setProperty(new TblPropEx(new TblBorders(new InsideH(
                Attr.get("w", "val", "nil"),
                Attr.get("w", "color", "000000"),
                Attr.get("w", "sz", "0")))));
    }

    // 567 twentieths of a point = 1 centimeter
    public static double getTableWidthInCentimeters(Tbl tbl) {
        return tbl.findFirst(TblGrid.class)
                .map(tblGrid -> tblGrid.asStream(GridCol.class))
                .map(stream -> stream.map(TableUtils::getGridColWidth))
                .map(integerStream -> integerStream.mapToDouble(integer -> integer).sum() / 567)
                .orElse((double) 0);
    }

    private static int getGridColWidth(GridCol gridCol) {
        return gridCol.findAttributeByName("w").map(attr -> Integer.valueOf(attr.value)).orElse(0);
    }

    private static boolean compareCellsContent(List<TCell> cells1, List<TCell> cells2) {
        if (cells1.size() != cells2.size()) {
            return false;
        }
        for (int i = 0; i < cells1.size(); i++) {
            TCell tCell1 = cells1.get(i);
            TCell tCell2 = cells2.get(i);
            if (!tCell1.getText().equals(tCell2.getText())) {
                return false;
            }
        }
        return true;
    }

    public static boolean isRowsContentEquals(TRow r1, TRow r2) {
        List<TCell> cells1 = r1.asStream(TCell.class).collect(Collectors.toList());
        List<TCell> cells2 = r2.asStream(TCell.class).collect(Collectors.toList());
        return compareCellsContent(cells1, cells2);
    }

    public static int getCellPositionCountingSpan(TCell tCell) {
        TRow tableRow = tCell.getParent().asTRow();
        List<TCell> tableCells = tableRow.asStream(TCell.class).collect(Collectors.toList());
        return getCellPositionCountingSpan(tCell, tableCells);
    }

    private static int getCellPositionCountingSpan(TCell tCell, List<TCell> cells) {
        int pos = 1;
        for (TCell currentTCell : cells) {
            if (tCell == currentTCell) {
                return pos;
            }
            int gridSpan = getGridSpanValue(currentTCell);
            pos += gridSpan == 0 ? 1 : gridSpan;
        }
        return -1;
    }

    public static <T extends Leaf> boolean tCellsIntoRowHaveInvisibleBorder(CDNode oldRow, Class<T> nodeClass) {
        List<T> list = oldRow.asStream(TCell.class)
                .flatMap(Composite::streamProperties)
                .flatMap(p -> p.asStream(TCellBorders.class)
                        .flatMap(b -> b.asStream(nodeClass)))
                .collect(Collectors.toList());

        int counter = 0;
        if(!list.isEmpty()) {
            for (T t : list) {
                List<Attr> listAttributes = t.attributes;
                if(t.attributes == null || t.attributes.isEmpty()) {
                    break;
                }
                for(Attr a : listAttributes) {
                    if(a.name.equals("val") && a.value.equals("nil")) {
                        counter++;
                    }
                }
            }
        }
        return list.size() !=0 && (list.size() == counter);
    }

    // TODO P1 2022-04-26 add unit tests
    public static List<String> getHeaders(Tbl table) {
        TRow tRow = getFirstTRow(table);
        return tRow.asStream(TCell.class)
                .map(CDNode::getText)
                .map(header -> header.replace("<*>", "").strip())
                .collect(Collectors.toList());
    }

    public static Set<String> getHeadersAsSet(Tbl table) {
        TRow tRow = getFirstTRow(table);
        return tRow.asStream(TCell.class)
                .map(CDNode::getText)
                .map(header -> header.replace("<*>", "").strip())
                .collect(Collectors.toSet());
    }

    public static boolean isNodeInsideTable(CDNode nodeParam) {
        CDNode node = nodeParam;
        while (node!=null) {
            if (!node.hasParent()) {
                return false;
            }
            Composite parent = node.getParent();
            if (parent instanceof TCell || parent instanceof TRow|| parent instanceof Tbl) {
                return true;
            }
            node = parent;
        }
        return false;
    }

    public static CDNode skipMergedRows(CDNode tRow) {
        CDNode currRow = tRow;
        while (currRow.hasNext()) {
            final boolean currMerged = getRowMerging(currRow).isPresent();
            if (!currMerged) {
                break;
            }
            final CDNode nextRow = currRow.getNext();
            final Optional<VMerge> nextRowMering = getRowMerging(nextRow);
            if (nextRowMering.isEmpty()) {
                break;
            }
            final List<Attr> attributes = nextRowMering.get().attributes;
            if (attributes != null && attributes.contains(Attr.get("w", "val", "restart"))) {
                break;
            }
            currRow = nextRow;
        }
        return currRow;
    }

    private static Optional<VMerge> getRowMerging(CDNode cdNode) {
        return cdNode.asComposite().findFirst(TCell.class).flatMap(tCell -> tCell.findProperty(TCellProp.class, VMerge.class));
    }

}
