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

import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.exceptions.TerminateApplicationException;
import ai.metaheuristic.ai.sec.AdditionalCustomUserDetails;
import ai.metaheuristic.ai.yaml.dispatcher_lookup.DispatcherLookupExtendedParams;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * @author Sergio Lissner
 * Date: 6/19/2023
 * Time: 9:46 PM
 */
@Service
@Slf4j
@Profile("processor")
//@RequiredArgsConstructor(onConstructor_={@Autowired})
public class ProcessorEnvironment {

    private final Globals globals;
    private final AdditionalCustomUserDetails additionalCustomUserDetails;
    private final ApplicationContext appCtx;

    public final EnvParams envParams = new EnvParams();
    public DispatcherLookupExtendedParams dispatcherLookupExtendedService;
    public MetadataParams metadataParams;

    @Autowired
    public ProcessorEnvironment(Globals globals, ApplicationContext appCtx, AdditionalCustomUserDetails additionalCustomUserDetails) {
        this.globals = globals;
        this.appCtx = appCtx;
        this.additionalCustomUserDetails = additionalCustomUserDetails;

//    @PostConstruct
//    public void init() {
        if (!globals.processor.enabled) {
            return;
        }

        try {
            final Path processorPath = globals.processorPath;
            final Path defaultDispatcherYamlFile = globals.processor.defaultDispatcherYamlFile;
            final Path defaultEnvYamlFile = globals.processor.defaultEnvYamlFile;
            final int taskConsoleOutputMaxLines = globals.processor.taskConsoleOutputMaxLines;

            if (defaultEnvYamlFile!=null && Files.notExists(defaultEnvYamlFile)) {
                log.warn("#747.030 Processor's default yaml.yaml file doesn't exist: {}", defaultEnvYamlFile.toAbsolutePath());
                throw new TerminateApplicationException("#747.014 Processor isn't configured, env.yaml is empty or doesn't exist");
            }
            EnvYamlProvider envYamlProvider = null;
            if (globals.standalone.active) {
                envYamlProvider = new StandaloneEnvYamlProvider();
            }
            else if (defaultEnvYamlFile!=null) {
                envYamlProvider = new FileEnvYamlProvider(defaultEnvYamlFile);
            }
            init(processorPath, envYamlProvider, defaultDispatcherYamlFile, taskConsoleOutputMaxLines);
        }
        catch (TerminateApplicationException e) {
            System.exit(SpringApplication.exit(appCtx, () -> -500));
        }
    }

    public void init(Path processorPath, @Nullable EnvYamlProvider envYamlProvider, @Nullable Path defaultDispatcherYamlFile, int taskConsoleOutputMaxLines) {
        envParams.init(processorPath, envYamlProvider, taskConsoleOutputMaxLines, !globals.standalone.active);
        dispatcherLookupExtendedService = globals.standalone.active
                ? new StandaloneDispatcherLookupExtendedParams(additionalCustomUserDetails.restUserPassword)
                : new FileDispatcherLookupExtendedParams(processorPath, defaultDispatcherYamlFile);
        metadataParams = new MetadataParams(processorPath, envParams, dispatcherLookupExtendedService);
    }
}
