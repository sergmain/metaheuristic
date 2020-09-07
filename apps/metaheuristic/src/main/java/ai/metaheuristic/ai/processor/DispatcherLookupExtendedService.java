/*
 * Metaheuristic, Copyright (C) 2017-2020  Serge Maslyukov
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
import ai.metaheuristic.ai.yaml.dispatcher_lookup.DispatcherLookupConfig;
import ai.metaheuristic.ai.yaml.dispatcher_lookup.DispatcherLookupConfigUtils;
import ai.metaheuristic.ai.yaml.dispatcher_lookup.DispatcherSchedule;
import ai.metaheuristic.ai.yaml.metadata.Metadata;
import ai.metaheuristic.commons.yaml.YamlUtils;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.Charsets;
import org.apache.commons.io.FileUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@Profile("processor")
public class DispatcherLookupExtendedService {

    private static final String DISPATCHERS_ROOT_PROPERTY = "dispatchers";
    private static final List<String> POSSIBLE_PROPS = List.of(
            "taskProcessingTime", "disabled", "url", "signatureRequired", "publicKey", "lookupType",
            "authType", "restPassword", "restUsername", "asset", "acceptOnlySignedFunctions"
    );
    private static final List<String> DEPRECATED_PROPS = List.of("acceptOnlySignedFunctions");

    private static final String SEE_MORE_INFO = "See https://docs.metaheuristic.ai/p/description-of-dispatcher-yaml for more info about structure of this file.\n";

    private final Globals globals;

    // Collections.unmodifiableMap
    public Map<String, DispatcherLookupExtended> lookupExtendedMap = Map.of();

    @Data
    public static class DispatcherLookupExtended {
        public DispatcherLookupConfig.DispatcherLookup dispatcherLookup;
        public DispatcherSchedule schedule;
        public final DispatcherContext context = new DispatcherContext();
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
                    "File dispatcher.yaml wan't found. " +
                    "It must be configured in directory "+globals.processorDir+" or be provided via application parameter mh.processor.default-dispatcher-yaml-file ");
        }

        try {
            cfg = FileUtils.readFileToString(dispatcherFile, Charsets.UTF_8);
        } catch (IOException e) {
            log.error("Error", e);
            throw new IllegalStateException("Error while reading file: " + dispatcherFile.getAbsolutePath(), e);
        }

        validateStructureOfDispatcherYaml(cfg);

        DispatcherLookupConfig dispatcherLookupConfig = DispatcherLookupConfigUtils.to(cfg);

        if (dispatcherLookupConfig == null) {
            log.error("{} wasn't found or empty. path: {}{}{}",
                    Consts.DISPATCHER_YAML_FILE_NAME, globals.processorDir,
                    File.separatorChar, Consts.DISPATCHER_YAML_FILE_NAME);
            throw new IllegalStateException("Processor isn't configured, dispatcher.yaml is empty or doesn't exist");
        }
        final Map<String, DispatcherLookupExtended> map = new HashMap<>();
        for (DispatcherLookupConfig.DispatcherLookup dispatcher : dispatcherLookupConfig.dispatchers) {
            DispatcherLookupExtended lookupExtended = new DispatcherLookupExtended();
            lookupExtended.dispatcherLookup = dispatcher;
            lookupExtended.schedule = new DispatcherSchedule(dispatcher.taskProcessingTime);
            map.put(dispatcher.url, lookupExtended);
        }
        lookupExtendedMap = Collections.unmodifiableMap(map);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void validateStructureOfDispatcherYaml(String cfg) {
        Yaml yaml = YamlUtils.init(Map.class);
        Map m = (Map) YamlUtils.to(cfg, yaml);

        if (!m.containsKey(DISPATCHERS_ROOT_PROPERTY)) {
            String es = "\n\n!!! Root element 'dispatchers' wasn't found in config file dispatcher.yaml\n"+SEE_MORE_INFO;
            log.error(es);
            System.exit(-1);
        }

        Object rootObj = m.get(DISPATCHERS_ROOT_PROPERTY);
        if (!(rootObj instanceof List)) {
            log.error("\nBroken content of dispatcher.yaml config file. Must be in .yaml format.\n" + SEE_MORE_INFO);
            System.exit(-1);
        }

        boolean isError = false;
        for (Object o : (List) rootObj) {
            if (!(o instanceof Map)) {
                log.error("\nBroken content of dispatcher.yaml config file. Must be in .yaml format.\n" + SEE_MORE_INFO);
                System.exit(-1);
            }

            Map<String, Object> props = (Map)o;
            for (Map.Entry<String, Object> entry : props.entrySet()) {
                if (!POSSIBLE_PROPS.contains(entry.getKey())) {
                    log.error("\ndispatcher.yaml, unknown property: " + entry.getKey());
                    isError=true;
                }
                if (DEPRECATED_PROPS.contains(entry.getKey())) {
                    log.error("\n\tproperty '" + entry.getKey()+"' is deprecated and has to be removed from config.");
                }
            }
        }

        if (isError) {
            log.error("\nUnknown properties was encountered in the config file dispatcher.yaml.\n" +
                    "Need to be fixed.\n" +
                    "Allowed profiles are: " + POSSIBLE_PROPS + "\n" +
                    SEE_MORE_INFO);
            System.exit(-1);
        }
    }

    public File prepareBaseResourceDir(Metadata.DispatcherInfo dispatcherCode) {
        final File dispatcherDir = new File(globals.processorResourcesDir, dispatcherCode.code);
        if (dispatcherDir.exists()) {
            return dispatcherDir;
        }
        //noinspection unused
        boolean status = dispatcherDir.mkdirs();
        return dispatcherDir;
    }


}
