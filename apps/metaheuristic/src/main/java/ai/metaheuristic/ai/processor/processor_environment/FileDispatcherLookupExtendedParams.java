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

package ai.metaheuristic.ai.processor.processor_environment;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.exceptions.TerminateApplicationException;
import ai.metaheuristic.ai.yaml.dispatcher_lookup.DispatcherLookupExtendedParams;
import ai.metaheuristic.ai.yaml.dispatcher_lookup.DispatcherLookupParamsYaml;
import ai.metaheuristic.ai.yaml.dispatcher_lookup.DispatcherLookupParamsYamlUtils;
import ai.metaheuristic.commons.yaml.YamlSchemeValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

import static ai.metaheuristic.commons.yaml.YamlSchemeValidator.Element;
import static ai.metaheuristic.commons.yaml.YamlSchemeValidator.Scheme;

@Slf4j
public class FileDispatcherLookupExtendedParams extends DispatcherLookupExtendedParams {

    private static final String SEE_MORE_INFO = "See https://docs.metaheuristic.ai/p/description-of-dispatcher-yaml for more info about structure of this file.\n";

    // verifying a structure of ai.metaheuristic.ai.yaml.dispatcher_lookup.DispatcherLookupParamsYaml
    public static final List<Scheme> SCHEMES = List.of(
            new Scheme(
                    List.of(
                            new Element(
                                    "dispatchers",
                                    true, false,
                                    List.of(new Element("taskProcessingTime", false, false),
                                            new Element("disabled", false, false),
                                            new Element("priority", false, false),
                                            new Element("url"),
                                            new Element("signatureRequired", false, false),
                                            new Element("publicKey", false, false),
                                            new Element("lookupType"),
                                            new Element("authType"),
                                            new Element("restPassword"),
                                            new Element("restUsername"),
                                            new Element("asset", false, false, new String[]{"url", "username", "password", "publicKey"}),
                                            new Element("acceptOnlySignedFunctions", false, true))
                                    )
                    ),
                    1,
                    SEE_MORE_INFO, true),
            new Scheme(
                    List.of(
                            new Element(
                                    "dispatchers",
                                    true, false,
                                    List.of(new Element("taskProcessingTime", false, false),
                                            new Element("disabled", false, false),
                                            new Element("priority", false, false),
                                            new Element("signatureRequired", false, false),
                                            new Element("lookupType", false, false),
                                            new Element("url"),
                                            new Element("publicKey", false, false),
                                            new Element("authType"),
                                            new Element("restPassword"),
                                            new Element("restUsername"),
                                            new Element("assetManagerUrl"))
                                    ),
                            new Element(
                                    "assetManagers",
                                    true, false,
                                    List.of(new Element("url"),
                                            new Element("username"),
                                            new Element("password"),
                                            new Element("publicKey"),
                                            new Element("disabled", false, false))
                            )
                    ),
                    2,
                    SEE_MORE_INFO, true)

    );

    public FileDispatcherLookupExtendedParams(Path processorPath, @Nullable Path defaultDispatcherYamlFile) {
        super(getDispatcherLookupParamsYaml(processorPath, defaultDispatcherYamlFile));
    }

    private static DispatcherLookupParamsYaml getDispatcherLookupParamsYaml(Path processorPath, @Nullable Path defaultDispatcherYamlFile) {
        DispatcherLookupParamsYaml dispatcherLookupConfig = getDispatcherLookupParamsYamlNullable(processorPath, defaultDispatcherYamlFile);
        return dispatcherLookupConfig == null ? new DispatcherLookupParamsYaml() : dispatcherLookupConfig;
    }

    @Nullable
    private static DispatcherLookupParamsYaml getDispatcherLookupParamsYamlNullable(Path processorPath, @Nullable Path defaultDispatcherYamlFile) {
        final Path dispatcherFile = processorPath.resolve(Consts.DISPATCHER_YAML_FILE_NAME);
        final String cfg;
        if (Files.notExists(dispatcherFile)) {
            if (defaultDispatcherYamlFile ==null) {
                log.error("Processor's dispatcher config file {} doesn't exist and default file wasn't specified", dispatcherFile);
                return null;
            }
            if (Files.notExists(defaultDispatcherYamlFile)) {
                log.error("Processor's default dispatcher.yaml file doesn't exist: {}", defaultDispatcherYamlFile.toAbsolutePath());
                return null;
            }
            try {
                Files.copy(defaultDispatcherYamlFile, dispatcherFile, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException e) {
                log.error("Error", e);
                throw new IllegalStateException("Error while copying " + defaultDispatcherYamlFile.toAbsolutePath() + " to " + dispatcherFile.normalize(), e);
            }
        }
        if (Files.notExists(dispatcherFile)) {
            throw new IllegalStateException(
                    "File dispatcher.yaml wasn't found. " +
                    "It must be configured in directory " + processorPath + " or be provided via application parameter mh.processor.default-dispatcher-yaml-file ");
        }

        try {
            cfg = Files.readString(dispatcherFile);
        } catch (IOException e) {
            log.error("Error", e);
            throw new IllegalStateException("Error while reading file: " + dispatcherFile.normalize(), e);
        }

        final YamlSchemeValidator<Boolean> YAML_SCHEME_VALIDATOR = new YamlSchemeValidator<> (
                SCHEMES,
                "the config file dispatcher.yaml",
                (es)-> {
                    throw new TerminateApplicationException();
                },
                SEE_MORE_INFO
        );

        if (Boolean.TRUE.equals(YAML_SCHEME_VALIDATOR.validateStructureOfDispatcherYaml(cfg))) {
            return null;
        }

        DispatcherLookupParamsYaml dispatcherLookupConfig = DispatcherLookupParamsYamlUtils.BASE_YAML_UTILS.to(cfg);

        if (dispatcherLookupConfig == null) {
            log.error("{} wasn't found or empty. path: {}/{}",
                    Consts.DISPATCHER_YAML_FILE_NAME, processorPath, Consts.DISPATCHER_YAML_FILE_NAME);
            throw new IllegalStateException("Processor isn't configured, dispatcher.yaml is empty or doesn't exist");
        }
        return dispatcherLookupConfig;
    }
}
