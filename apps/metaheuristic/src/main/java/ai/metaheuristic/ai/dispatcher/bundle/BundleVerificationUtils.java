/*
 * Metaheuristic, Copyright (C) 2017-2022, Innovation platforms, LLC
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

package ai.metaheuristic.ai.dispatcher.bundle;

import ai.metaheuristic.commons.yaml.YamlSchemeValidator;

import java.util.List;

/**
 * @author Serge
 * Date: 5/28/2022
 * Time: 3:20 AM
 */
public class BundleVerificationUtils {
    private static final String SEE_MORE_INFO = "See https://docs.metaheuristic.ai/p/function#configuration.\n";
    public static final YamlSchemeValidator<String> FUNCTION_CONFIG_LIST_YAML_SCHEME_VALIDATOR = new YamlSchemeValidator<> (
            List.of( new YamlSchemeValidator.Scheme(
                    List.of( new YamlSchemeValidator.Element(
                            "functions",
                            true, false,
                            List.of(
                                    new YamlSchemeValidator.Element("code"),
                                    new YamlSchemeValidator.Element("env", false, false),
                                    new YamlSchemeValidator.Element("file", false, false),
                                    new YamlSchemeValidator.Element("git", false, false),
                                    new YamlSchemeValidator.Element("params", false, false),
                                    new YamlSchemeValidator.Element("metas", false, false),
                                    new YamlSchemeValidator.Element("skipParams", false, false),
                                    new YamlSchemeValidator.Element("sourcing"),
                                    new YamlSchemeValidator.Element("type", false, false),
                                    new YamlSchemeValidator.Element("checksumMap", false, false))
                    )),1, SEE_MORE_INFO),
                    new YamlSchemeValidator.Scheme(List.of( new YamlSchemeValidator.Element(
                            "functions",
                            true, false,
                            List.of(
                                    new YamlSchemeValidator.Element("code"),
                                    new YamlSchemeValidator.Element("env", false, false),
                                    new YamlSchemeValidator.Element("file", false, false),
                                    new YamlSchemeValidator.Element("git", false, false),
                                    new YamlSchemeValidator.Element("params", false, false),
                                    new YamlSchemeValidator.Element("metas", false, false),
                                    new YamlSchemeValidator.Element("skipParams", false, false),
                                    new YamlSchemeValidator.Element("sourcing"),
                                    new YamlSchemeValidator.Element("type", false, false),
                                    new YamlSchemeValidator.Element("content", false, false),
                                    new YamlSchemeValidator.Element("checksumMap", false, false))
                    ) ),2, SEE_MORE_INFO),
                    new YamlSchemeValidator.Scheme(List.of( new YamlSchemeValidator.Element(
                            "functions",
                            true, false,
                            List.of(
                                    new YamlSchemeValidator.Element("code"),
                                    new YamlSchemeValidator.Element("env", false, false),
                                    new YamlSchemeValidator.Element("file", false, false),
                                    new YamlSchemeValidator.Element("git", false, false),
                                    new YamlSchemeValidator.Element("params", false, false),
                                    new YamlSchemeValidator.Element("metas", false, false),
                                    new YamlSchemeValidator.Element("skipParams", false, false),
                                    new YamlSchemeValidator.Element("sourcing"),
                                    new YamlSchemeValidator.Element("type", false, false),
                                    new YamlSchemeValidator.Element("content", false, false),
                                    new YamlSchemeValidator.Element("checksumMap", false, false))
                            ), new YamlSchemeValidator.Element(
                            "sourceCodes",
                            false, false,
                            List.of(
                                    new YamlSchemeValidator.Element("file"),
                                    new YamlSchemeValidator.Element("lang", false, false))
                            )
                    ),3, SEE_MORE_INFO)
            ),
            "the config file bundle.yaml",
            (es)-> es, SEE_MORE_INFO
    );
}
