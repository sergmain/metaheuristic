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

package ai.metaheuristic.ai.processor;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.commons.dispatcher_schedule.DispatcherSchedule;
import ai.metaheuristic.ai.yaml.dispatcher_lookup.DispatcherLookupParamsYaml;
import ai.metaheuristic.ai.yaml.dispatcher_lookup.DispatcherLookupParamsYamlUtils;
import ai.metaheuristic.commons.utils.SecUtils;
import ai.metaheuristic.commons.yaml.YamlSchemeValidator;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static ai.metaheuristic.ai.processor.ProcessorAndCoreData.DispatcherUrl;
import static ai.metaheuristic.commons.yaml.YamlSchemeValidator.Element;
import static ai.metaheuristic.commons.yaml.YamlSchemeValidator.Scheme;

@Service
@Slf4j
@Profile("processor")
//@DependsOn({"Globals"})
public class DispatcherLookupExtendedService {

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
                    SEE_MORE_INFO),
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
                    SEE_MORE_INFO)

    );

    // Collections.unmodifiableMap
    public final Map<DispatcherUrl, DispatcherLookupExtended> lookupExtendedMap;
    public final Map<ProcessorAndCoreData.AssetManagerUrl, DispatcherLookupParamsYaml.AssetManager> assets = new HashMap<>();

    @Data
    @AllArgsConstructor
    public static class DispatcherLookupExtended {
        public final DispatcherUrl dispatcherUrl;
        public final DispatcherLookupParamsYaml.DispatcherLookup dispatcherLookup;
        public final DispatcherSchedule schedule;
        private final Map<Integer, PublicKey> publicKeyMap = new HashMap<>();

        public PublicKey getPublicKey() {
            return publicKeyMap.computeIfAbsent(1, k-> SecUtils.getPublicKey(dispatcherLookup.publicKey));
        }
    }

    // TODO 2021-03-29 investigate why this service isn't using anymore
    public DispatcherLookupExtendedService(Globals globals, ApplicationContext appCtx) {
        Map<DispatcherUrl, DispatcherLookupExtended> dispatcherLookupExtendedMap = Map.of();
        try {
            final File dispatcherFile = new File(globals.processor.dir.dir, Consts.DISPATCHER_YAML_FILE_NAME);
            final String cfg;
            if (!dispatcherFile.exists()) {
                if (globals.processor.defaultDispatcherYamlFile == null) {
                    log.error("Processor's dispatcher config file {} doesn't exist and default file wasn't specified", dispatcherFile.getPath());
                    return;
                }
                if (!globals.processor.defaultDispatcherYamlFile.exists()) {
                    log.error("Processor's default dispatcher.yaml file doesn't exist: {}", globals.processor.defaultDispatcherYamlFile.getAbsolutePath());
                    return;
                }
                try {
                    FileUtils.copyFile(globals.processor.defaultDispatcherYamlFile, dispatcherFile);
                } catch (IOException e) {
                    log.error("Error", e);
                    throw new IllegalStateException("Error while copying "+ globals.processor.defaultDispatcherYamlFile.getAbsolutePath()+" to " + dispatcherFile.getAbsolutePath(), e);
                }
            }
            if (!dispatcherFile.exists()) {
                throw new IllegalStateException(
                        "File dispatcher.yaml wasn't found. " +
                        "It must be configured in directory "+globals.processor.dir.dir+" or be provided via application parameter mh.processor.default-dispatcher-yaml-file ");
            }

            try {
                cfg = FileUtils.readFileToString(dispatcherFile, StandardCharsets.UTF_8);
            } catch (IOException e) {
                log.error("Error", e);
                throw new IllegalStateException("Error while reading file: " + dispatcherFile.getAbsolutePath(), e);
            }

            final YamlSchemeValidator<Boolean> YAML_SCHEME_VALIDATOR = new YamlSchemeValidator<> (
                    SCHEMES,
                    "the config file dispatcher.yaml",
                    (es)-> {
                        System.exit(SpringApplication.exit(appCtx, () -> -500));
                        return true;
                    },
                    SEE_MORE_INFO
            );

            if (Boolean.TRUE.equals(YAML_SCHEME_VALIDATOR.validateStructureOfDispatcherYaml(cfg))) {
                return;
            }

            DispatcherLookupParamsYaml dispatcherLookupConfig = DispatcherLookupParamsYamlUtils.BASE_YAML_UTILS.to(cfg);

            if (dispatcherLookupConfig == null) {
                log.error("{} wasn't found or empty. path: {}{}{}",
                        Consts.DISPATCHER_YAML_FILE_NAME, globals.processor.dir.dir,
                        File.separatorChar, Consts.DISPATCHER_YAML_FILE_NAME);
                throw new IllegalStateException("Processor isn't configured, dispatcher.yaml is empty or doesn't exist");
            }
            final Map<DispatcherUrl, DispatcherLookupExtended> map = new HashMap<>();
            for (DispatcherLookupParamsYaml.DispatcherLookup dispatcher : dispatcherLookupConfig.dispatchers) {
                DispatcherUrl dispatcherServerUrl = new DispatcherUrl(dispatcher.url);
                DispatcherLookupExtended lookupExtended = new DispatcherLookupExtended(dispatcherServerUrl, dispatcher, new DispatcherSchedule(dispatcher.taskProcessingTime));
                map.put(dispatcherServerUrl, lookupExtended);
            }
            dispatcherLookupExtendedMap = Collections.unmodifiableMap(map);
            dispatcherLookupConfig.assetManagers.forEach(asset -> assets.put(new ProcessorAndCoreData.AssetManagerUrl(asset.url), asset));
        }
        finally {
            lookupExtendedMap = dispatcherLookupExtendedMap;
        }
    }

    @Nullable
    public DispatcherLookupExtended getDispatcher(DispatcherUrl dispatcherUrl) {
        return lookupExtendedMap.get(dispatcherUrl);
    }

    public List<DispatcherUrl> getAllEnabledDispatchers() {
        return lookupExtendedMap.values().stream().filter(o->!o.dispatcherLookup.disabled).map(o->o.dispatcherUrl).collect(Collectors.toList());
    }

    @Nullable
    public DispatcherLookupParamsYaml.AssetManager getAssetManager(ProcessorAndCoreData.AssetManagerUrl assetManagerUrl) {
        return assets.get(assetManagerUrl);
    }

}
