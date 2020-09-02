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
import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.utils.asset.AssetFile;
import ai.metaheuristic.ai.utils.asset.AssetUtils;
import ai.metaheuristic.ai.processor.function.ProcessorFunctionService;
import ai.metaheuristic.ai.yaml.communication.dispatcher.DispatcherCommParamsYaml;
import ai.metaheuristic.ai.yaml.communication.processor.ProcessorCommParamsYaml;
import ai.metaheuristic.ai.yaml.dispatcher_lookup.DispatcherLookupConfig;
import ai.metaheuristic.ai.yaml.metadata.Metadata;
import ai.metaheuristic.ai.yaml.metadata.MetadataUtils;
import ai.metaheuristic.ai.yaml.metadata.FunctionDownloadStatusYaml;
import ai.metaheuristic.ai.yaml.metadata.FunctionDownloadStatusYamlUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.utils.Checksum;
import ai.metaheuristic.commons.utils.checksum.CheckSumAndSignatureStatus;
import ai.metaheuristic.commons.utils.checksum.ChecksumWithSignatureUtils;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
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

@Service
@Slf4j
@Profile("processor")
@RequiredArgsConstructor
public class MetadataService {

    private final Globals globals;
    private final DispatcherLookupExtendedService dispatcherLookupExtendedService;
    private final ProcessorFunctionService processorFunctionService;

    private Metadata metadata = null;

    @Data
    public static class ChecksumState {
        public Enums.ChecksumStateEnum state = Enums.ChecksumStateEnum.unknown;
        public Checksum checksum;
    }

    @Data
    @AllArgsConstructor
    private static class SimpleCache {
        public final DispatcherLookupExtendedService.DispatcherLookupExtended dispatcher;
        public final DispatcherLookupConfig.Asset asset;
        public final Metadata.DispatcherInfo dispatcherInfo;
        public final File baseResourceDir;
    }

    private final Map<String, SimpleCache> simpleCacheMap = new HashMap<>();

    @PostConstruct
    public void init() {
      final File metadataFile = new File(globals.processorDir, Consts.METADATA_YAML_FILE_NAME);
        if (metadataFile.exists()) {
            String yaml = null;
            try {
                yaml = FileUtils.readFileToString(metadataFile, StandardCharsets.UTF_8);
                metadata = MetadataUtils.to(yaml);
            } catch (org.yaml.snakeyaml.reader.ReaderException e) {
                log.error("#815.020 Bad data in " + metadataFile.getAbsolutePath()+"\nYaml:\n" + yaml);
                throw new IllegalStateException("#815.015 Error while loading file: " + metadataFile.getPath(), e);
            } catch (IOException e) {
                log.error("#815.040 Error", e);
                throw new IllegalStateException("#815.060 Error while loading file: " + metadataFile.getPath(), e);
            }
        }
        if (metadata==null) {
            metadata = new Metadata();
        }
        for (Map.Entry<String, DispatcherLookupExtendedService.DispatcherLookupExtended> entry : dispatcherLookupExtendedService.lookupExtendedMap.entrySet()) {
            dispatcherUrlAsCode(entry.getKey());
        }
        // update metadata.yaml file after fixing brocked metas
        updateMetadataFile();
        markAllAsUnverified();
        //noinspection unused
        int i=0;
    }

    public ChecksumState prepareChecksum(boolean signatureRequired, String functionCode, String dispatcherUrl, TaskParamsYaml.FunctionConfig functionConfig) {
        ChecksumState checksumState = new ChecksumState();
        if (!signatureRequired) {
            checksumState.state= Enums.ChecksumStateEnum.signature_not_required;
            return checksumState;
        }

        // check requirements of signature
        if (S.b(functionConfig.checksum)) {
            setFunctionState(dispatcherUrl, functionCode, Enums.FunctionState.signature_not_found);
            checksumState.state = Enums.ChecksumStateEnum.signature_not_valid;
            return checksumState;
        }
        checksumState.checksum = Checksum.fromJson(functionConfig.checksum);
        boolean isSignature = checksumState.checksum.checksums.entrySet().stream().anyMatch(e -> e.getKey().isSign);
        if (!isSignature) {
            setFunctionState(dispatcherUrl, functionCode, Enums.FunctionState.signature_not_found);
            checksumState.state = Enums.ChecksumStateEnum.signature_not_valid;
            return checksumState;
        }
        return checksumState;
    }

    private void markAllAsUnverified() {
        List<FunctionDownloadStatusYaml.Status > statuses = getFunctionDownloadStatusYamlInternal().statuses;
        for (FunctionDownloadStatusYaml.Status status : statuses) {
            if (status.sourcing!= EnumsApi.FunctionSourcing.dispatcher) {
                continue;
            }

            final String dispatcherUrl = status.dispatcherUrl;
            final String functionCode = status.code;

            setVerifiedStatus(dispatcherUrl, functionCode, false);
        }
    }

    @Nullable
    public FunctionDownloadStatusYaml.Status syncFunctionStatus(String dispatcherUrl, DispatcherLookupConfig.Asset asset, final String functionCode) {
        try {
            syncFunctionStatusInternal(dispatcherUrl, asset, functionCode);
        } catch (Throwable th) {
            log.error("#815.080 Error in syncFunctionStatus()", th);
            setFunctionState(dispatcherUrl, functionCode, Enums.FunctionState.io_error);
        }
        return getFunctionDownloadStatuses(dispatcherUrl, functionCode);
    }

    private void syncFunctionStatusInternal(String dispatcherUrl, DispatcherLookupConfig.Asset asset, String functionCode) {
        FunctionDownloadStatusYaml.Status status = getFunctionDownloadStatuses(dispatcherUrl, functionCode);

        if (status == null || status.sourcing != EnumsApi.FunctionSourcing.dispatcher || status.verified) {
            return;
        }
        SimpleCache simpleCache = simpleCacheMap.computeIfAbsent(dispatcherUrl, o -> {
            final Metadata.DispatcherInfo dispatcherInfo = dispatcherUrlAsCode(dispatcherUrl);
            return new SimpleCache(
                    dispatcherLookupExtendedService.lookupExtendedMap.get(dispatcherUrl),
                    asset,
                    dispatcherInfo,
                    dispatcherLookupExtendedService.prepareBaseResourceDir(dispatcherInfo)
            );
        });

        ProcessorFunctionService.DownloadedFunctionConfigStatus downloadedFunctionConfigStatus =
                processorFunctionService.downloadFunctionConfig(dispatcherUrl, simpleCache.asset, functionCode, simpleCache.dispatcherInfo.processorId);

        if (downloadedFunctionConfigStatus.status == ProcessorFunctionService.ConfigStatus.error) {
            setFunctionState(dispatcherUrl, functionCode, Enums.FunctionState.function_config_error);
            return;
        }
        if (downloadedFunctionConfigStatus.status == ProcessorFunctionService.ConfigStatus.not_found) {
            removeFunction(dispatcherUrl, functionCode);
            return;
        }
        TaskParamsYaml.FunctionConfig functionConfig = downloadedFunctionConfigStatus.functionConfig;

        ChecksumState checksumState = prepareChecksum(simpleCache.dispatcher.dispatcherLookup.signatureRequired, functionCode, dispatcherUrl, functionConfig);
        if (checksumState.state==Enums.ChecksumStateEnum.signature_not_valid) {
            return;
        }

        final AssetFile assetFile = AssetUtils.prepareFunctionFile(simpleCache.baseResourceDir, status.code, functionConfig.file);
        if (assetFile.isError) {
            setFunctionState(dispatcherUrl, functionCode, Enums.FunctionState.asset_error);
            return;
        }
        if (!assetFile.isContent) {
            setFunctionState(dispatcherUrl, functionCode, Enums.FunctionState.none);
            return;
        }

        try {
            CheckSumAndSignatureStatus checkSumAndSignatureStatus = getCheckSumAndSignatureStatus(
                    functionCode, simpleCache.dispatcher.dispatcherLookup, checksumState, assetFile.file);

            if (checkSumAndSignatureStatus.checksum == CheckSumAndSignatureStatus.Status.correct && checkSumAndSignatureStatus.signature == CheckSumAndSignatureStatus.Status.correct) {
                if (status.functionState != Enums.FunctionState.ready || !status.verified) {
                    setFunctionState(dispatcherUrl, functionCode, Enums.FunctionState.ready);
                }
            }
        } catch (Throwable th) {
            log.error(S.f("#815.100 Error verifying function %s from %s", functionCode, dispatcherUrl), th);
            setFunctionState(dispatcherUrl, functionCode, Enums.FunctionState.io_error);
        }
    }

    public CheckSumAndSignatureStatus getCheckSumAndSignatureStatus(
            String functionCode, DispatcherLookupConfig.DispatcherLookup dispatcher, ChecksumState checksumState, File functionTempFile) throws IOException {
        CheckSumAndSignatureStatus status = new CheckSumAndSignatureStatus(CheckSumAndSignatureStatus.Status.correct, CheckSumAndSignatureStatus.Status.correct);
        if (checksumState.state!=Enums.ChecksumStateEnum.signature_not_required) {
            try (FileInputStream fis = new FileInputStream(functionTempFile)) {
                status = ChecksumWithSignatureUtils.verifyChecksumAndSignature(checksumState.checksum, "Dispatcher url: "+ dispatcher.url +", function: "+functionCode, fis, true, dispatcher.createPublicKey());
            }
            if (status.signature != CheckSumAndSignatureStatus.Status.correct) {
                log.warn("#815.120 dispatcher.acceptOnlySignedFunctions is {} but function {} has the broken signature", dispatcher.acceptOnlySignedFunctions, functionCode);
                setFunctionState(dispatcher.url, functionCode, Enums.FunctionState.signature_wrong);
            }
            else if (status.checksum != CheckSumAndSignatureStatus.Status.correct) {
                log.warn("#815.140 dispatcher.acceptOnlySignedFunctions is {} but function {} has the broken signature", dispatcher.acceptOnlySignedFunctions, functionCode);
                setFunctionState(dispatcher.url, functionCode, Enums.FunctionState.checksum_wrong);
            }
        }
        return status;
    }

    @Nullable
    public String findHostByCode(String code) {
        synchronized (syncObj) {
            for (Map.Entry<String, Metadata.DispatcherInfo> entry : metadata.dispatcher.entrySet()) {
                if (code.equals(entry.getValue().code)) {
                    return entry.getKey();
                }
            }
            return null;
        }
    }

    private static final Object syncObj = new Object();

    public Metadata.DispatcherInfo dispatcherUrlAsCode(String dispatcherUrl) {
        synchronized (syncObj) {
            Metadata.DispatcherInfo dispatcherInfo = getDispatcherInfo(dispatcherUrl);
            // fix for wrong metadata.yaml data
            if (dispatcherInfo.code == null) {
                dispatcherInfo.code = asCode(dispatcherUrl).code;
                updateMetadataFile();
            }
            return dispatcherInfo;
        }
    }

    public String getProcessorId(final String dispatcherUrl) {
        synchronized (syncObj) {
            return getDispatcherInfo(dispatcherUrl).processorId;
        }
    }

    public void setProcessorIdAndSessionId(final String dispatcherUrl, String processorId, String sessionId) {
        if (StringUtils.isBlank(dispatcherUrl)) {
            throw new IllegalStateException("#815.160 dispatcherUrl is null");
        }
        if (StringUtils.isBlank(processorId)) {
            throw new IllegalStateException("#815.180 processorId is null");
        }
        synchronized (syncObj) {
            final Metadata.DispatcherInfo dispatcherInfo = getDispatcherInfo(dispatcherUrl);
            if (!Objects.equals(dispatcherInfo.processorId, processorId) || !Objects.equals(dispatcherInfo.sessionId, sessionId)) {
                dispatcherInfo.processorId = processorId;
                dispatcherInfo.sessionId = sessionId;
                updateMetadataFile();
            }
        }
    }

    public List<FunctionDownloadStatusYaml.Status> registerNewFunctionCode(String dispatcherUrl, List<DispatcherCommParamsYaml.Functions.Info> infos) {
        if (S.b(dispatcherUrl)) {
            throw new IllegalStateException("#815.200 dispatcherUrl is null");
        }
        FunctionDownloadStatusYaml functionDownloadStatusYaml;
        synchronized (syncObj) {
            functionDownloadStatusYaml = getFunctionDownloadStatusYamlInternal();
            boolean isChanged = false;
            for (DispatcherCommParamsYaml.Functions.Info info : infos) {
                FunctionDownloadStatusYaml.Status status = functionDownloadStatusYaml.statuses.stream()
                        .filter(o->o.dispatcherUrl.equals(dispatcherUrl) && o.code.equals(info.code))
                        .findAny().orElse(null);
                if (status==null || status.functionState == Enums.FunctionState.not_found) {
                    setFunctionDownloadStatusInternal(dispatcherUrl, info.code, info.sourcing, Enums.FunctionState.none);
                    isChanged = true;
                }
            }

            // set state to FunctionState.not_found if function doesn't exist at Dispatcher any more
            for (FunctionDownloadStatusYaml.Status status : functionDownloadStatusYaml.statuses) {
                if (status.dispatcherUrl.equals(dispatcherUrl) && infos.stream().filter(i-> i.code.equals(status.code)).findAny().orElse(null)==null) {
                    setFunctionDownloadStatusInternal(dispatcherUrl, status.code, status.sourcing, Enums.FunctionState.not_found);
                    isChanged = true;
                }
            }
            if (isChanged) {
                updateMetadataFile();
            }
        }
        return functionDownloadStatusYaml.statuses;
    }

    public boolean setFunctionState(final String assetUrl, String functionCode, Enums.FunctionState functionState) {
        if (S.b(assetUrl)) {
            throw new IllegalStateException("#815.220 assetUrl is null");
        }
        if (S.b(functionCode)) {
            throw new IllegalStateException("#815.240 functionCode is null");
        }
        synchronized (syncObj) {
            FunctionDownloadStatusYaml functionDownloadStatusYaml = getFunctionDownloadStatusYamlInternal();

            FunctionDownloadStatusYaml.Status status = functionDownloadStatusYaml.statuses.stream().filter(o->o.dispatcherUrl.equals(assetUrl) && o.code.equals(functionCode)).findAny().orElse(null);
            if (status == null) {
                return false;
            }
            status.functionState = functionState;
            status.verified = true;
            String yaml = FunctionDownloadStatusYamlUtils.BASE_YAML_UTILS.toString(functionDownloadStatusYaml);
            metadata.metadata.put(Consts.META_FUNCTION_DOWNLOAD_STATUS, yaml);
            updateMetadataFile();
            return true;
        }
    }

    public boolean removeFunction(final String dispatcherUrl, String functionCode) {
        if (S.b(dispatcherUrl)) {
            throw new IllegalStateException("#815.260 dispatcherUrl is null");
        }
        if (S.b(functionCode)) {
            throw new IllegalStateException("#815.280 functionCode is empty");
        }
        synchronized (syncObj) {
            FunctionDownloadStatusYaml functionDownloadStatusYaml = getFunctionDownloadStatusYamlInternal();
            List<FunctionDownloadStatusYaml.Status> statuses = functionDownloadStatusYaml.statuses.stream().filter(o->!(o.dispatcherUrl.equals(dispatcherUrl) && o.code.equals(functionCode))).collect(Collectors.toList());
            if (statuses.size()!= functionDownloadStatusYaml.statuses.size()) {
                functionDownloadStatusYaml.statuses = statuses;
                String yaml = FunctionDownloadStatusYamlUtils.BASE_YAML_UTILS.toString(functionDownloadStatusYaml);
                metadata.metadata.put(Consts.META_FUNCTION_DOWNLOAD_STATUS, yaml);
                updateMetadataFile();
            }
            return true;
        }
    }

    public boolean setVerifiedStatus(final String dispatcherUrl, String functionCode, boolean verified) {
        if (S.b(dispatcherUrl)) {
            throw new IllegalStateException("#815.300 dispatcherUrl is null");
        }
        if (S.b(functionCode)) {
            throw new IllegalStateException("#815.320 functionCode is null");
        }
        synchronized (syncObj) {
            FunctionDownloadStatusYaml functionDownloadStatusYaml = getFunctionDownloadStatusYamlInternal();

            FunctionDownloadStatusYaml.Status status = functionDownloadStatusYaml.statuses.stream().filter(o->o.dispatcherUrl.equals(dispatcherUrl) && o.code.equals(functionCode)).findAny().orElse(null);
            if (status == null) {
                return false;
            }
            status.verified = verified;
            String yaml = FunctionDownloadStatusYamlUtils.BASE_YAML_UTILS.toString(functionDownloadStatusYaml);
            metadata.metadata.put(Consts.META_FUNCTION_DOWNLOAD_STATUS, yaml);
            updateMetadataFile();
            return true;
        }
    }

    public void setFunctionDownloadStatus(final String dispatcherUrl, String functionCode, EnumsApi.FunctionSourcing sourcing, Enums.FunctionState functionState) {
        if (S.b(dispatcherUrl)) {
            throw new IllegalStateException("#815.340 dispatcherUrl is null");
        }
        if (S.b(functionCode)) {
            throw new IllegalStateException("#815.360 functionCode is empty");
        }
        synchronized (syncObj) {
            setFunctionDownloadStatusInternal(dispatcherUrl, functionCode, sourcing, functionState);
            updateMetadataFile();
        }
    }

    // it could be null if this function was deleted
    @Nullable
    public FunctionDownloadStatusYaml.Status getFunctionDownloadStatuses(String dispatcherUrl, String functionCode) {
        synchronized (syncObj) {
            return getFunctionDownloadStatusYamlInternal().statuses.stream()
                    .filter(o->o.dispatcherUrl.equals(dispatcherUrl) && o.code.equals(functionCode))
                    .findAny().orElse(null);
        }
    }

    public List<ProcessorCommParamsYaml.FunctionDownloadStatus.Status> getAsFunctionDownloadStatuses(final String dispatcherUrl) {
        synchronized (syncObj) {
            return getFunctionDownloadStatusYamlInternal().statuses.stream()
                    .filter(o->o.dispatcherUrl.equals(dispatcherUrl))
                    .map(o->new ProcessorCommParamsYaml.FunctionDownloadStatus.Status(o.functionState, o.code))
                    .collect(Collectors.toList());
        }
    }

    private void setFunctionDownloadStatusInternal(String dispatcherUrl, String code, EnumsApi.FunctionSourcing sourcing, Enums.FunctionState functionState) {
        FunctionDownloadStatusYaml functionDownloadStatusYaml = getFunctionDownloadStatusYamlInternal();

        FunctionDownloadStatusYaml.Status status = functionDownloadStatusYaml.statuses.stream().filter(o->o.dispatcherUrl.equals(dispatcherUrl) && o.code.equals(code)).findAny().orElse(null);
        if (status == null) {
            status = new FunctionDownloadStatusYaml.Status(Enums.FunctionState.none, code, dispatcherUrl, sourcing, false);
            functionDownloadStatusYaml.statuses.add(status);
        }
        status.functionState = functionState;
        String yaml = FunctionDownloadStatusYamlUtils.BASE_YAML_UTILS.toString(functionDownloadStatusYaml);
        metadata.metadata.put(Consts.META_FUNCTION_DOWNLOAD_STATUS, yaml);
    }

    public FunctionDownloadStatusYaml getFunctionDownloadStatusYaml() {
        synchronized (syncObj) {
            FunctionDownloadStatusYaml functionDownloadStatusYaml = getFunctionDownloadStatusYamlInternal();
            return functionDownloadStatusYaml;
        }
    }

    private FunctionDownloadStatusYaml getFunctionDownloadStatusYamlInternal() {
        String yaml = metadata.metadata.get(Consts.META_FUNCTION_DOWNLOAD_STATUS);
        FunctionDownloadStatusYaml functionDownloadStatusYaml;
        if (S.b(yaml)) {
            functionDownloadStatusYaml = new FunctionDownloadStatusYaml();
        }
        else {
            functionDownloadStatusYaml = FunctionDownloadStatusYamlUtils.BASE_YAML_UTILS.to(yaml);
            if (functionDownloadStatusYaml == null) {
                functionDownloadStatusYaml = new FunctionDownloadStatusYaml();
            }
        }
        functionDownloadStatusYaml.statuses.sort(Comparator.comparingInt(c -> c.sourcing.value));
        return functionDownloadStatusYaml;
    }

    public String getSessionId(final String dispatcherUrl) {
        synchronized (syncObj) {
            return getDispatcherInfo(dispatcherUrl).sessionId;
        }
    }

    public void setSessionId(final String dispatcherUrl, String sessionId) {
        if (StringUtils.isBlank(dispatcherUrl)) {
            throw new IllegalStateException("#815.380 dispatcherUrl is null");
        }
        if (StringUtils.isBlank(sessionId)) {
            throw new IllegalStateException("#815.400 sessionId is null");
        }
        synchronized (syncObj) {
            getDispatcherInfo(dispatcherUrl).processorId = sessionId;
            updateMetadataFile();
        }
    }

    private Metadata.DispatcherInfo getDispatcherInfo(String dispatcherUrl) {
        synchronized (syncObj) {
            return metadata.dispatcher.computeIfAbsent(dispatcherUrl, m -> asCode(dispatcherUrl));
        }
    }

    private void updateMetadataFile() {
        final File metadataFile =  new File(globals.processorDir, Consts.METADATA_YAML_FILE_NAME);
        if (metadataFile.exists()) {
            log.trace("#815.420 Metadata file exists. Make backup");
            File yamlFileBak = new File(globals.processorDir, Consts.METADATA_YAML_FILE_NAME + ".bak");
            //noinspection ResultOfMethodCallIgnored
            yamlFileBak.delete();
            if (metadataFile.exists()) {
                //noinspection ResultOfMethodCallIgnored
                metadataFile.renameTo(yamlFileBak);
            }
        }

        try {
            String data = MetadataUtils.toString(metadata);
            FileUtils.write(metadataFile, data, StandardCharsets.UTF_8, false);
            String check = FileUtils.readFileToString(metadataFile, StandardCharsets.UTF_8);
            if (!check.equals(data)) {
                log.warn("#815.440 Metadata was persisted with an error, content is different, size - expected: {}, actual: {}", data.length(), check.length());
            }
        } catch (IOException e) {
            log.error("#815.460 Error", e);
            throw new IllegalStateException("#815.480 Error while writing to file: " + metadataFile.getPath(), e);
        }
    }

    private Metadata.DispatcherInfo asCode(String dispatcherUrl) {
        String s = dispatcherUrl.toLowerCase();
        if (dispatcherUrl.startsWith(Consts.HTTP)) {
            s = s.substring(Consts.HTTP.length());
        }
        else if (dispatcherUrl.startsWith(Consts.HTTPS)) {
            s = s.substring(Consts.HTTPS.length());
        }
        s = StringUtils.replaceEach(s, new String[]{".", ":", "/"}, new String[]{"_", "-", "-"});
        Metadata.DispatcherInfo dispatcherInfo = new Metadata.DispatcherInfo();
        dispatcherInfo.code = s;
        return dispatcherInfo;
    }

}
