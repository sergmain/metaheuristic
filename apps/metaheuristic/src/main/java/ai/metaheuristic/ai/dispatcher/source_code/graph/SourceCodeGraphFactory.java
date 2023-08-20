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

package ai.metaheuristic.ai.dispatcher.source_code.graph;

import ai.metaheuristic.ai.dispatcher.data.SourceCodeData;
import ai.metaheuristic.api.EnumsApi;

import java.util.function.Supplier;

/**
 * @author Serge
 * Date: 2/14/2020
 * Time: 10:50 PM
 */
public class SourceCodeGraphFactory {

    private final static SourceCodeGraphLanguageYaml YAML_LANG = new SourceCodeGraphLanguageYaml();

    public static SourceCodeData.SourceCodeGraph parse(EnumsApi.SourceCodeLang lang, String sourceCode, Supplier<String> contextIdSupplier) {
        //noinspection SwitchStatementWithTooFewBranches
        switch (lang) {
            case yaml:
                return YAML_LANG.parse(sourceCode, contextIdSupplier);
            default:
                throw new IllegalStateException("Unknown language dialect: " + lang);
        }
    }
}
