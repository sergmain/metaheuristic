/*
 * Metaheuristic, Copyright (C) 2017-2021, Innovation platforms, LLC
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

package ai.metaheuristic.ai.dispatcher.source_code;

/**
 * @author Serge
 * Date: 10/31/2021
 * Time: 9:30 PM
 */
public class SourceCodeDiffUtils {

    // TODO 2021-10-31 the knowledge of which idea had to be implemented here was lost
/*
    public void diff(Long sourceCodeId1, Long sourceCodeId2) {
        SourceCodeImpl sc1 = sourceCodeCache.findById(sourceCodeId1);
        if (sc1==null) {
            return;
        }
        SourceCodeImpl sc2 = sourceCodeCache.findById(sourceCodeId2);
        if (sc2==null) {
            return;
        }

        SourceCodeStoredParamsYaml scspy = sc1.getSourceCodeStoredParamsYaml();
        SourceCodeParamsYaml scpy = SourceCodeParamsYamlUtils.BASE_YAML_UTILS.to(scspy.source);

        DiffRowGenerator generator = DiffRowGenerator.create()
                .showInlineDiffs(true)
                .mergeOriginalRevised(false)
                .inlineDiffByWord(true)
                .reportLinesUnchanged(true)
                .oldTag(f -> "~")      //introduce markdown style for strikethrough
                .newTag(f -> "**")     //introduce markdown style for bold
                .build();

        //compute the differences for two sourceCodes.
        List<DiffRow> rows = generator.generateDiffRows(
                List.of(sc1.getSourceCodeStoredParamsYaml().source),
                List.of(sc2.getSourceCodeStoredParamsYaml().source));

        for (DiffRow row : rows) {
            final String oldLine = row.getOldLine();
            final String newLine = row.getNewLine();
            if (!oldLine.equals(newLine)) {
                System.out.println("- " + oldLine);
                System.out.println("+ " + newLine);
            }
            else {
                System.out.println("  " + oldLine);
            }
        }
    }
*/

}
