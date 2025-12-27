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

package ai.metaheuristic.ai.utils;

import ai.metaheuristic.commons.S;
import org.springframework.lang.NonNull;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * @author Sergio Lissner
 * Date: 9/12/2023
 * Time: 10:11 PM
 */
public class StatusUtils {

    /**
     * print as text table - https://itsallbinary.com/java-printing-to-console-in-table-format-simple-code-with-flexible-width-left-align-header-separator-line/
     *
     * @param consumeFunc PrintStream
     * @param maxWidth Maximum allowed width. Line will be wrapped beyond this width.
     * @param leftJustifiedRows If true, it will add "-" as a flag to format string to make it left justified. Otherwise right justified.
     */
    public static void printTable(Consumer<String> consumeFunc, boolean leftJustifiedRows, int maxWidth, boolean emptyRowSeparator, String[][] table) {
        /*
         * Create new table array with wrapped rows
         */
        List<String[]> tableList = new ArrayList<>(Arrays.asList(table));
        List<String[]> finalTableList = new ArrayList<>();
        for (String[] row : tableList) {
            // If any cell data is more than max width, then it will need extra row.
            boolean needExtraRow = false;
            // Count of extra split row.
            int splitRow = 0;
            boolean firstString = true;
            do {
                needExtraRow = false;
                String[] newRow = new String[row.length];
                for (int i = 0; i < row.length; i++) {
                    // If data is less than max width, use that as it is.
                    if (row[i].length() < maxWidth) {
                        newRow[i] = splitRow == 0 ? row[i] : "";
                    } else if ((row[i].length() > (splitRow * maxWidth))) {
                        // If data is more than max width, then crop data at maxwidth.
                        // Remaining cropped data will be part of next row.
                        int end = Math.min(row[i].length(), ((splitRow * maxWidth) + maxWidth));
                        newRow[i] = row[i].substring((splitRow * maxWidth), end);
                        needExtraRow = true;
                    } else {
                        newRow[i] = "";
                    }
                }
                if (firstString) {
                    needExtraRow = true;
                    firstString = false;
                }
                finalTableList.add(newRow);
                if (needExtraRow) {
                    splitRow++;
                }
            } while (needExtraRow);
        }
        String[][] finalTable = new String[finalTableList.size()][finalTableList.get(0).length];
        for (int i = 0; i < finalTable.length; i++) {
            finalTable[i] = finalTableList.get(i);
        }

        final Map<Integer, Integer> columnLengths = calcColumnLengths(consumeFunc, finalTable);
        final String formatingString = makeFormatingString(consumeFunc, leftJustifiedRows, columnLengths);
        final String line = makeSelectorLine(consumeFunc, columnLengths);

        /*
         * Print table
         */
        consumeFunc.accept(line);
        Arrays.stream(finalTable).limit(1).forEach(a -> consumeFunc.accept(S.f(formatingString, (Object[]) a)));

        for (int i = 1;  i < finalTable.length ; ++i) {
            if (allEmpty(finalTable[i]) && !emptyRowSeparator) {
                consumeFunc.accept(line);
            }
            else {
                consumeFunc.accept(S.f(formatingString, (Object[]) finalTable[i]));
            }
        }
        if (!allEmpty(finalTable[finalTable.length-1])) {
            consumeFunc.accept(line);
        }
    }

    private static boolean allEmpty(String[] strings) {
        return Arrays.stream(strings).allMatch(String::isBlank);
    }

    @NonNull
    private static String makeFormatingString(Consumer<String> consumeFunc, boolean leftJustifiedRows, Map<Integer, Integer> columnLengths) {
        /*
         * Prepare format String
         */
        final StringBuilder formatString = new StringBuilder();
        String flag = leftJustifiedRows ? "-" : "";
        columnLengths.forEach((key, value) -> formatString.append("| %").append(flag).append(value).append("s "));
        formatString.append("|\n");
        final String formatStringString = formatString.toString();
//        out.println("formatString = " + formatStringString);
        return formatStringString;
    }


    @NonNull
    private static Map<Integer, Integer> calcColumnLengths(Consumer<String> consumeFunc, String[][] finalTable) {
        /*
         * Calculate appropriate Length of each column by looking at width of data in
         * each column.
         *
         * Map columnLengths is <column_number, column_length>
         */
        Map<Integer, Integer> columnLengths = new HashMap<>();
        Arrays.stream(finalTable).forEach(a -> Stream.iterate(0, (i -> i < a.length), (i -> ++i)).forEach(i -> {
            columnLengths.putIfAbsent(i, 0);
            if (columnLengths.get(i) < a[i].length()) {
                columnLengths.put(i, a[i].length());
            }
        }));
//        out.println("columnLengths = " + columnLengths);
        return columnLengths;
    }

    @NonNull
    private static String makeSelectorLine(Consumer<String> consumeFunc, Map<Integer, Integer> columnLengths) {
        /*
         * Prepare line for top, bottom & below header row.
         */
        String line = columnLengths.entrySet().stream().reduce("", (ln, b) -> {
            String templn = "+-";
            templn = templn + Stream.iterate(0, (i -> i < b.getValue()), (i -> ++i)).reduce("", (ln1, b1) -> ln1 + "-",
                (a1, b1) -> a1 + b1);
            templn = templn + "-";
            return ln + templn;
        }, (a, b) -> a + b);
        line = line + "+\n";
//        out.println("Line = " + line);
        return line;
    }
}
