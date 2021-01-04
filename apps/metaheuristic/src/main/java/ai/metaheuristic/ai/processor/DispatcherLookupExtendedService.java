/*
 * Metaheuristic, Copyright (C) 2017-2020, Innovation platforms, LLC
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

package ai.metaheuristic.ai.processor;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.commons.dispatcher_schedule.DispatcherSchedule;
import ai.metaheuristic.ai.yaml.dispatcher_lookup.DispatcherLookupParamsYaml;
import ai.metaheuristic.ai.yaml.dispatcher_lookup.DispatcherLookupParamsYamlUtils;
import ai.metaheuristic.commons.yaml.YamlSchemeValidator;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.Charsets;
import org.apache.commons.io.FileUtils;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static ai.metaheuristic.ai.processor.ProcessorAndCoreData.DispatcherUrl;

@Service
@Slf4j
@Profile("processor")
@DependsOn({"Globals"})
public class DispatcherLookupExtendedService {

    @SuppressWarnings("FieldCanBeLocal")
    private final Globals globals;

    private static final String SEE_MORE_INFO = "See https://docs.metaheuristic.ai/p/description-of-dispatcher-yaml for more info about structure of this file.\n";
    private static final YamlSchemeValidator<Void> YAML_SCHEME_VALIDATOR = new YamlSchemeValidator<> (
            "dispatchers",
            List.of(
                    "taskProcessingTime", "disabled", "url", "signatureRequired", "publicKey", "lookupType",
                    "authType", "restPassword", "restUsername", "asset", "acceptOnlySignedFunctions"
            ),
            List.of("acceptOnlySignedFunctions"),
            SEE_MORE_INFO, List.of("1"),
            "the config file dispatcher.yaml",
            (es)-> {System.exit(-1); return null;}
    );

    // Collections.unmodifiableMap
    public Map<DispatcherUrl, DispatcherLookupExtended> lookupExtendedMap = Map.of();
    public final Map<ProcessorAndCoreData.AssetManagerUrl, DispatcherLookupParamsYaml.AssetManager> assets = new HashMap<>();

    @Data
    @AllArgsConstructor
    public static class DispatcherLookupExtended {
        public final DispatcherUrl dispatcherUrl;
        public final DispatcherLookupParamsYaml.DispatcherLookup dispatcherLookup;
        public final DispatcherSchedule schedule;
    }

    public DispatcherLookupExtendedService(Globals globals) {
        this.globals = globals;
        final File dispatcherFile = new File(globals.processorDir, Consts.DISPATCHER_YAML_FILE_NAME);
        final String cfg;
        if (!dispatcherFile.exists()) {
            if (globals.defaultDispatcherYamlFile == null) {
                log.error("Processor's dispatcher config file {} doesn't exist and default file wasn't specified", dispatcherFile.getPath());
                return;
            }
            if (!globals.defaultDispatcherYamlFile.exists()) {
                log.error("Processor's default dispatcher.yaml file doesn't exist: {}", globals.defaultDispatcherYamlFile.getAbsolutePath());
                return;
            }
            try {
                FileUtils.copyFile(globals.defaultDispatcherYamlFile, dispatcherFile);
            } catch (IOException e) {
                log.error("Error", e);
                throw new IllegalStateException("Error while copying "+ globals.defaultDispatcherYamlFile.getAbsolutePath()+" to " + dispatcherFile.getAbsolutePath(), e);
            }
        }
        if (!dispatcherFile.exists()) {
            throw new IllegalStateException(
                    "File dispatcher.yaml wasn't found. " +
                    "It must be configured in directory "+globals.processorDir+" or be provided via application parameter mh.processor.default-dispatcher-yaml-file ");
        }

        try {
            cfg = FileUtils.readFileToString(dispatcherFile, Charsets.UTF_8);
        } catch (IOException e) {
            log.error("Error", e);
            throw new IllegalStateException("Error while reading file: " + dispatcherFile.getAbsolutePath(), e);
        }

        YAML_SCHEME_VALIDATOR.validateStructureOfDispatcherYaml(cfg);

        DispatcherLookupParamsYaml dispatcherLookupConfig = DispatcherLookupParamsYamlUtils.BASE_YAML_UTILS.to(cfg);

        if (dispatcherLookupConfig == null) {
            log.error("{} wasn't found or empty. path: {}{}{}",
                    Consts.DISPATCHER_YAML_FILE_NAME, globals.processorDir,
                    File.separatorChar, Consts.DISPATCHER_YAML_FILE_NAME);
            throw new IllegalStateException("Processor isn't configured, dispatcher.yaml is empty or doesn't exist");
        }
        final Map<DispatcherUrl, DispatcherLookupExtended> map = new HashMap<>();
        for (DispatcherLookupParamsYaml.DispatcherLookup dispatcher : dispatcherLookupConfig.dispatchers) {
            DispatcherUrl dispatcherServerUrl = new DispatcherUrl(dispatcher.url);
            DispatcherLookupExtended lookupExtended = new DispatcherLookupExtended(dispatcherServerUrl, dispatcher, new DispatcherSchedule(dispatcher.taskProcessingTime));
            map.put(dispatcherServerUrl, lookupExtended);
        }
        lookupExtendedMap = Collections.unmodifiableMap(map);
        dispatcherLookupConfig.assetManagers.forEach(asset -> assets.put(new ProcessorAndCoreData.AssetManagerUrl(asset.url), asset));
    }

    @Nullable
    public DispatcherLookupParamsYaml.AssetManager getAssetManager(ProcessorAndCoreData.AssetManagerUrl assetManagerUrl) {
        return assets.get(assetManagerUrl);
    }

}
