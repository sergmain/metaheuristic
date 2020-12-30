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
import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.processor.function.ProcessorFunctionService;
import ai.metaheuristic.ai.utils.asset.AssetFile;
import ai.metaheuristic.ai.utils.asset.AssetUtils;
import ai.metaheuristic.ai.yaml.communication.keep_alive.KeepAliveRequestParamYaml;
import ai.metaheuristic.ai.yaml.communication.keep_alive.KeepAliveResponseParamYaml;
import ai.metaheuristic.ai.yaml.dispatcher_lookup.DispatcherLookupConfig;
import ai.metaheuristic.ai.yaml.metadata.MetadataParamsYaml;
import ai.metaheuristic.ai.yaml.metadata.MetadataParamsYamlUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.DispatcherApiData;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.utils.checksum.CheckSumAndSignatureStatus;
import ai.metaheuristic.commons.utils.checksum.ChecksumWithSignatureUtils;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

import static ai.metaheuristic.ai.processor.ProcessorAndCoreData.*;

@SuppressWarnings("UnusedReturnValue")
@Service
@Slf4j
@Profile("processor")
@RequiredArgsConstructor
public class MetadataService {

    private final ApplicationContext appCtx;
    private final Globals globals;
    private final DispatcherLookupExtendedService dispatcherLookupExtendedService;
    private final ProcessorFunctionService processorFunctionService;

    private MetadataParamsYaml metadata = null;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChecksumWithSignatureState {
        public Enums.ChecksumStateEnum state = Enums.ChecksumStateEnum.unknown;
        public ChecksumWithSignatureUtils.ChecksumWithSignature checksumWithSignature;
        public String originChecksumWithSignature;
        public EnumsApi.HashAlgo hashAlgo;

        public ChecksumWithSignatureState(Enums.ChecksumStateEnum state) {
            this.state = state;
        }
    }

    @Data
    @AllArgsConstructor
    private static class SimpleCache {
        public final DispatcherLookupExtendedService.DispatcherLookupExtended dispatcher;
        public final DispatcherLookupConfig.Asset asset;
        public final MetadataParamsYaml.ProcessorState processorState;
        public final File baseResourceDir;
    }

    private final Map<String, SimpleCache> simpleCacheMap = new HashMap<>();

    private static class MetadataSync {}
    private static final MetadataSync syncObj = new MetadataSync();

    @PostConstruct
    public void init() {
        final File metadataFile = new File(globals.processorDir, Consts.METADATA_YAML_FILE_NAME);
        if (metadataFile.exists()) {
            String yaml = null;
            try {
                yaml = FileUtils.readFileToString(metadataFile, StandardCharsets.UTF_8);
                metadata = MetadataParamsYamlUtils.BASE_YAML_UTILS.to(yaml);
            } catch (org.yaml.snakeyaml.reader.ReaderException e) {
                log.error("#815.020 Bad data in " + metadataFile.getAbsolutePath()+"\nYaml:\n" + yaml);
                System.exit(SpringApplication.exit(appCtx, () -> -500));
            } catch (Throwable e) {
                log.error("#815.040 Error", e);
                System.exit(SpringApplication.exit(appCtx, () -> -500));
            }
        }
        if (metadata==null) {
            metadata = new MetadataParamsYaml();
        }
        for (Map.Entry<DispatcherServerUrl, DispatcherLookupExtendedService.DispatcherLookupExtended> entry : dispatcherLookupExtendedService.lookupExtendedMap.entrySet()) {
            dispatcherUrlAsCode(entry.getKey());
        }
        // update metadata.yaml file after fixing broken metas
        updateMetadataFile();
        markAllAsUnverified();
        //noinspection unused
        int i=0;
    }

    public ChecksumWithSignatureState prepareChecksumWithSignature(boolean signatureRequired, String functionCode, AssetServerUrl assetUrl, TaskParamsYaml.FunctionConfig functionConfig) {
        if (!signatureRequired) {
            return new ChecksumWithSignatureState(Enums.ChecksumStateEnum.signature_not_required);
        }

        // check requirements of signature
        if (functionConfig.checksumMap==null) {
            return setSignatureNotValid(functionCode, assetUrl);
        }

        // at 2020-09-02, only HashAlgo.SHA256WithSignature is supported for signing
        String data = functionConfig.checksumMap.entrySet().stream()
                .filter(o -> o.getKey() == EnumsApi.HashAlgo.SHA256WithSignature)
                .findFirst()
                .map(Map.Entry::getValue).orElse(null);

        if (S.b(data)) {
            return setSignatureNotValid(functionCode, assetUrl);
        }
        ChecksumWithSignatureUtils.ChecksumWithSignature checksumWithSignature = ChecksumWithSignatureUtils.parse(data);
        if (S.b(checksumWithSignature.checksum) || S.b(checksumWithSignature.signature)) {
            return setSignatureNotValid(functionCode, assetUrl);
        }

        return new ChecksumWithSignatureState(Enums.ChecksumStateEnum.signature_ok, checksumWithSignature, data, EnumsApi.HashAlgo.SHA256WithSignature);
    }

    private ChecksumWithSignatureState setSignatureNotValid(String functionCode, AssetServerUrl assetUrl) {
        setFunctionState(assetUrl, functionCode, Enums.FunctionState.signature_not_found);
        return new ChecksumWithSignatureState(Enums.ChecksumStateEnum.signature_not_valid);
    }

    private void markAllAsUnverified() {
        for (MetadataParamsYaml.Status status : metadata.statuses) {
            if (status.sourcing!= EnumsApi.FunctionSourcing.dispatcher) {
                continue;
            }
            status.verified = false;
        }
        updateMetadataFile();
    }

    @Nullable
    public MetadataParamsYaml.Status syncFunctionStatus(ServerUrls urls, DispatcherLookupConfig.Asset asset, final String functionCode) {
        try {
            syncFunctionStatusInternal(urls, asset, functionCode);
        } catch (Throwable th) {
            log.error("#815.080 Error in syncFunctionStatus()", th);
            setFunctionState(urls.assetUrl, functionCode, Enums.FunctionState.io_error);
        }
        return getFunctionDownloadStatuses(urls.assetUrl, functionCode);
    }

    private void syncFunctionStatusInternal(ServerUrls serverUrls, DispatcherLookupConfig.Asset asset, String functionCode) {
        MetadataParamsYaml.Status status = getFunctionDownloadStatuses(serverUrls.assetUrl, functionCode);

        if (status == null || status.sourcing != EnumsApi.FunctionSourcing.dispatcher || status.verified) {
            return;
        }
        SimpleCache simpleCache = simpleCacheMap.computeIfAbsent(serverUrls.dispatcherUrl.url, o -> {
            final MetadataParamsYaml.ProcessorState processorState = dispatcherUrlAsCode(serverUrls.dispatcherUrl);
            return new SimpleCache(
                    dispatcherLookupExtendedService.lookupExtendedMap.get(serverUrls.dispatcherUrl.url),
                    asset,
                    processorState,
                    dispatcherLookupExtendedService.prepareBaseResourceDir(processorState)
            );
        });

        ProcessorFunctionService.DownloadedFunctionConfigStatus downloadedFunctionConfigStatus =
                processorFunctionService.downloadFunctionConfig(serverUrls.dispatcherUrl, simpleCache.asset, functionCode);

        if (downloadedFunctionConfigStatus.status == ProcessorFunctionService.ConfigStatus.error) {
            setFunctionState(serverUrls.assetUrl, functionCode, Enums.FunctionState.function_config_error);
            return;
        }
        if (downloadedFunctionConfigStatus.status == ProcessorFunctionService.ConfigStatus.not_found) {
            removeFunction(serverUrls.assetUrl, functionCode);
            return;
        }
        TaskParamsYaml.FunctionConfig functionConfig = downloadedFunctionConfigStatus.functionConfig;

        ChecksumWithSignatureState checksumState = prepareChecksumWithSignature(simpleCache.dispatcher.dispatcherLookup.signatureRequired, functionCode, serverUrls.assetUrl, functionConfig);
        if (checksumState.state==Enums.ChecksumStateEnum.signature_not_valid) {
            return;
        }

        final AssetFile assetFile = AssetUtils.prepareFunctionFile(simpleCache.baseResourceDir, status.code, functionConfig.file);
        if (assetFile.isError) {
            setFunctionState(serverUrls.assetUrl, functionCode, Enums.FunctionState.asset_error);
            return;
        }
        if (!assetFile.isContent) {
            setFunctionState(serverUrls.assetUrl, functionCode, Enums.FunctionState.none);
            return;
        }

        try {
            CheckSumAndSignatureStatus checkSumAndSignatureStatus = getCheckSumAndSignatureStatus(serverUrls.assetUrl,
                    functionCode, simpleCache.dispatcher.dispatcherLookup, checksumState, assetFile.file);

            if (checkSumAndSignatureStatus.checksum == CheckSumAndSignatureStatus.Status.correct && checkSumAndSignatureStatus.signature == CheckSumAndSignatureStatus.Status.correct) {
                if (status.functionState != Enums.FunctionState.ready || !status.verified) {
                    setFunctionState(serverUrls.assetUrl, functionCode, Enums.FunctionState.ready);
                }
            }
        } catch (Throwable th) {
            log.error(S.f("#815.100 Error verifying function %s from asset server %s, dispatcher %s", functionCode, serverUrls.assetUrl, serverUrls.dispatcherUrl), th);
            setFunctionState(serverUrls.assetUrl, functionCode, Enums.FunctionState.io_error);
        }
    }

    public CheckSumAndSignatureStatus getCheckSumAndSignatureStatus(
            AssetServerUrl assetUrl,
            String functionCode, DispatcherLookupConfig.DispatcherLookup dispatcher, ChecksumWithSignatureState checksumState, File functionTempFile) throws IOException {
        CheckSumAndSignatureStatus status = new CheckSumAndSignatureStatus(CheckSumAndSignatureStatus.Status.correct, CheckSumAndSignatureStatus.Status.correct);
        if (checksumState.state!=Enums.ChecksumStateEnum.signature_not_required) {
            try (FileInputStream fis = new FileInputStream(functionTempFile)) {
                status = ChecksumWithSignatureUtils.verifyChecksumAndSignature(
                        "Dispatcher url: "+ dispatcher.getDispatcherUrl() +", function: "+functionCode, fis, dispatcher.createPublicKey(),
                        checksumState.originChecksumWithSignature, checksumState.hashAlgo);
            }
            if (status.signature != CheckSumAndSignatureStatus.Status.correct) {
                log.warn("#815.120 dispatcher.signatureRequired is {} but function {} has the broken signature", dispatcher.signatureRequired, functionCode);
                setFunctionState(assetUrl, functionCode, Enums.FunctionState.signature_wrong);
            }
            else if (status.checksum != CheckSumAndSignatureStatus.Status.correct) {
                log.warn("#815.140 dispatcher.signatureRequired is {} but function {} has the wrong checksum", dispatcher.signatureRequired, functionCode);
                setFunctionState(assetUrl, functionCode, Enums.FunctionState.checksum_wrong);
            }
        }
        return status;
    }

    @Nullable
    public DispatcherServerUrl findHostByCode(String code) {
        synchronized (syncObj) {
            for (Map.Entry<String, MetadataParamsYaml.ProcessorState> entry : metadata.processorStates.entrySet()) {
                if (code.equals(entry.getValue().dispatcherCode)) {
                    return new DispatcherServerUrl(entry.getKey());
                }
            }
            return null;
        }
    }

    public MetadataParamsYaml.ProcessorState dispatcherUrlAsCode(DispatcherServerUrl dispatcherUrl) {
        synchronized (syncObj) {
            MetadataParamsYaml.ProcessorState dispatcherInfo = getDispatcherInfo(dispatcherUrl);
            // fix for wrong metadata.yaml data
            if (dispatcherInfo.dispatcherCode == null) {
                dispatcherInfo.dispatcherCode = asEmptyProcessorSate(dispatcherUrl).dispatcherCode;
                updateMetadataFile();
            }
            return dispatcherInfo;
        }
    }

    public String getProcessorId(final DispatcherServerUrl dispatcherUrl) {
        synchronized (syncObj) {
            return getDispatcherInfo(dispatcherUrl).processorId;
        }
    }

    public DispatcherApiData.ProcessorSessionId getProcessorSessionId(final DispatcherServerUrl dispatcherUrl) {
        synchronized (syncObj) {
            MetadataParamsYaml.ProcessorState processorState = getDispatcherInfo(dispatcherUrl);
            return new DispatcherApiData.ProcessorSessionId(Long.valueOf(processorState.processorId), processorState.sessionId);
        }
    }

    public void setProcessorIdAndSessionId(final DispatcherServerUrl dispatcherUrl, String processorId, String sessionId) {
        if (StringUtils.isBlank(processorId)) {
            throw new IllegalStateException("#815.180 processorId is null");
        }
        synchronized (syncObj) {
            final MetadataParamsYaml.ProcessorState processorState = getDispatcherInfo(dispatcherUrl);
            if (!Objects.equals(processorState.processorId, processorId) || !Objects.equals(processorState.sessionId, sessionId)) {
                processorState.processorId = processorId;
                processorState.sessionId = sessionId;
                updateMetadataFile();
            }
        }
    }

    public List<MetadataParamsYaml.Status> registerNewFunctionCode(DispatcherServerUrl dispatcherUrl, List<KeepAliveResponseParamYaml.Functions.Info> infos) {
        final DispatcherLookupExtendedService.DispatcherLookupExtended dispatcher =
                dispatcherLookupExtendedService.lookupExtendedMap.get(dispatcherUrl);

        AssetServerUrl assetUrl = new AssetServerUrl(dispatcher.dispatcherLookup.getAsset().url);

        synchronized (syncObj) {
            boolean isChanged = false;
            for (KeepAliveResponseParamYaml.Functions.Info info : infos) {
                MetadataParamsYaml.Status status = metadata.statuses.stream()
                        .filter(o->o.assetUrl.equals(assetUrl.url) && o.code.equals(info.code))
                        .findAny().orElse(null);
                if (status==null || status.functionState == Enums.FunctionState.not_found) {
                    setFunctionDownloadStatusInternal(assetUrl, info.code, info.sourcing, Enums.FunctionState.none);
                    isChanged = true;
                }
            }

            // set state to FunctionState.not_found if function doesn't exist at Dispatcher any more
            for (MetadataParamsYaml.Status status : metadata.statuses) {
                if (status.assetUrl.equals(assetUrl.url) && infos.stream().filter(i-> i.code.equals(status.code)).findAny().orElse(null)==null) {
                    setFunctionDownloadStatusInternal(assetUrl, status.code, status.sourcing, Enums.FunctionState.not_found);
                    isChanged = true;
                }
            }
            if (isChanged) {
                updateMetadataFile();
            }
        }
        return metadata.statuses;
    }

    public boolean setFunctionState(final AssetServerUrl assetUrl, String functionCode, Enums.FunctionState functionState) {
        if (S.b(functionCode)) {
            throw new IllegalStateException("#815.240 functionCode is null");
        }
        synchronized (syncObj) {
            MetadataParamsYaml.Status status = metadata.statuses.stream().filter(o -> o.assetUrl.equals(assetUrl.url)).filter(o-> o.code.equals(functionCode)).findAny().orElse(null);
            if (status == null) {
                return false;
            }
            status.functionState = functionState;
            status.verified = true;
            updateMetadataFile();
            return true;
        }
    }

    private boolean removeFunction(final AssetServerUrl assetUrl, String functionCode) {
        if (S.b(functionCode)) {
            throw new IllegalStateException("#815.280 functionCode is empty");
        }
        synchronized (syncObj) {
            List<MetadataParamsYaml.Status> statuses = metadata.statuses.stream().filter(o->!(o.assetUrl.equals(assetUrl.url) && o.code.equals(functionCode))).collect(Collectors.toList());
            if (statuses.size()!= metadata.statuses.size()) {
                metadata.statuses.clear();
                metadata.statuses.addAll(statuses);
                updateMetadataFile();
            }
            return true;
        }
    }

    public void setFunctionDownloadStatus(final AssetServerUrl assetUrl, String functionCode, EnumsApi.FunctionSourcing sourcing, Enums.FunctionState functionState) {
        if (S.b(functionCode)) {
            throw new IllegalStateException("#815.360 functionCode is empty");
        }
        synchronized (syncObj) {
            setFunctionDownloadStatusInternal(assetUrl, functionCode, sourcing, functionState);
            updateMetadataFile();
        }
    }

    // it could be null if this function was deleted
    @Nullable
    public MetadataParamsYaml.Status getFunctionDownloadStatuses(AssetServerUrl assetUrl, String functionCode) {
        synchronized (syncObj) {
            return metadata.statuses.stream()
                    .filter(o->o.assetUrl.equals(assetUrl.url) && o.code.equals(functionCode))
                    .findAny().orElse(null);
        }
    }

    public List<KeepAliveRequestParamYaml.FunctionDownloadStatuses.Status> getAsFunctionDownloadStatuses(final AssetServerUrl assetUrl) {
        synchronized (syncObj) {
            return metadata.statuses.stream()
                    .filter(o->o.assetUrl.equals(assetUrl.url))
                    .map(o->new KeepAliveRequestParamYaml.FunctionDownloadStatuses.Status(o.code, o.functionState))
                    .collect(Collectors.toList());
        }
    }

    private void setFunctionDownloadStatusInternal(AssetServerUrl assetUrl, String code, EnumsApi.FunctionSourcing sourcing, Enums.FunctionState functionState) {
        MetadataParamsYaml.Status status = metadata.statuses.stream().filter(o->o.assetUrl.equals(assetUrl.url) && o.code.equals(code)).findAny().orElse(null);
        if (status == null) {
            status = new MetadataParamsYaml.Status(Enums.FunctionState.none, code, assetUrl.url, sourcing, false);
            metadata.statuses.add(status);
        }
        status.functionState = functionState;
    }

    public List<MetadataParamsYaml.Status> getStatuses() {
        synchronized (syncObj) {
            return Collections.unmodifiableList(metadata.statuses);
        }
    }

    public String getSessionId(final DispatcherServerUrl dispatcherUrl) {
        synchronized (syncObj) {
            return getDispatcherInfo(dispatcherUrl).sessionId;
        }
    }

    @SuppressWarnings("unused")
    public void setSessionId(final DispatcherServerUrl dispatcherUrl, String sessionId) {
        if (StringUtils.isBlank(sessionId)) {
            throw new IllegalStateException("#815.400 sessionId is null");
        }
        synchronized (syncObj) {
            getDispatcherInfo(dispatcherUrl).processorId = sessionId;
            updateMetadataFile();
        }
    }

    private MetadataParamsYaml.ProcessorState getDispatcherInfo(DispatcherServerUrl dispatcherUrl) {
        synchronized (syncObj) {
            return metadata.processorStates.computeIfAbsent(dispatcherUrl.url, m -> asEmptyProcessorSate(dispatcherUrl));
        }
    }

    private void updateMetadataFile() {
        final File metadataFile =  new File(globals.processorDir, Consts.METADATA_YAML_FILE_NAME);
        if (metadataFile.exists()) {
            log.trace("#815.420 Metadata file exists. Make backup");
            File yamlFileBak = new File(globals.processorDir, Consts.METADATA_YAML_FILE_NAME + ".bak");
            //noinspection ResultOfMethodCallIgnored
            yamlFileBak.delete();
            //noinspection ResultOfMethodCallIgnored
            metadataFile.renameTo(yamlFileBak);
        }

        try {
            String data = MetadataParamsYamlUtils.BASE_YAML_UTILS.toString(metadata);
            FileUtils.writeStringToFile(metadataFile, data, StandardCharsets.UTF_8, false);
            String check = FileUtils.readFileToString(metadataFile, StandardCharsets.UTF_8);
            if (!check.equals(data)) {
                log.error("#815.440 Metadata was persisted with an error, content is different, size - expected: {}, actual: {}, Processor will be closed", data.length(), check.length());
                System.exit(SpringApplication.exit(appCtx, () -> -500));
            }
        } catch (Throwable th) {
            log.error("#815.460 Error, Processor will be closed", th);
            System.exit(SpringApplication.exit(appCtx, () -> -500));
        }
    }

    @SuppressWarnings("unused")
    private void restoreFromBackup() {
        log.info("#815.480 Trying to restore previous state of metadata.yaml");
        try {
            File yamlFileBak = new File(globals.processorDir, Consts.METADATA_YAML_FILE_NAME + ".bak");
            String content = FileUtils.readFileToString(yamlFileBak, StandardCharsets.UTF_8);
            File yamlFile = new File(globals.processorDir, Consts.METADATA_YAML_FILE_NAME);
            FileUtils.writeStringToFile(yamlFile, content, StandardCharsets.UTF_8, false);
        } catch (IOException e) {
            log.error("#815.500 restoring of metadata.yaml from backup was failed. Processor will be stopped.");
            System.exit(SpringApplication.exit(appCtx, () -> -500));
        }

    }

    private static MetadataParamsYaml.ProcessorState asEmptyProcessorSate(DispatcherServerUrl dispatcherUrl) {
        String s = dispatcherUrl.url.toLowerCase();
        if (dispatcherUrl.url.startsWith(Consts.HTTP)) {
            s = s.substring(Consts.HTTP.length());
        }
        else if (dispatcherUrl.url.startsWith(Consts.HTTPS)) {
            s = s.substring(Consts.HTTPS.length());
        }
        s = StringUtils.replaceEach(s, new String[]{".", ":", "/"}, new String[]{"_", "-", "-"});
        MetadataParamsYaml.ProcessorState processorState = new MetadataParamsYaml.ProcessorState();
        processorState.dispatcherCode = s;
        return processorState;
    }

}
