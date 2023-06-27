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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Path;

/**
 * @author Sergio Lissner
 * Date: 6/19/2023
 * Time: 9:46 PM
 */
@Service
@Slf4j
@Profile("processor")
public class ProcessorEnvironment {

    public final EnvParams envParams = new EnvParams();
    public DispatcherLookupExtendedParams dispatcherLookupExtendedService;
    public MetadataParams metadataService;

    public ProcessorEnvironment(@Autowired Globals globals, @Autowired ApplicationContext appCtx) {
        if (!globals.processor.enabled) {
            return;
        }

        try {
            final Path processorPath = globals.processorPath;
            final Path defaultEnvYamlFile = globals.processor.defaultEnvYamlFile;
            final int taskConsoleOutputMaxLines = globals.processor.taskConsoleOutputMaxLines;
            final Path defaultDispatcherYamlFile = globals.processor.defaultDispatcherYamlFile;
            init(processorPath, defaultEnvYamlFile, defaultDispatcherYamlFile, taskConsoleOutputMaxLines);
        }
        catch (TerminateApplicationException e) {
            System.exit(SpringApplication.exit(appCtx, () -> -500));
        }
    }

    public void init(Path processorPath, @Nullable Path defaultEnvYamlFile, @Nullable Path defaultDispatcherYamlFile, int taskConsoleOutputMaxLines) {
        envParams.init(processorPath, defaultEnvYamlFile, taskConsoleOutputMaxLines);
        dispatcherLookupExtendedService = new DispatcherLookupExtendedParams(processorPath, defaultDispatcherYamlFile);
        metadataService = new MetadataParams(processorPath, envParams, dispatcherLookupExtendedService);
    }
}
