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
import ai.metaheuristic.ai.processor.data.ProcessorData;
import ai.metaheuristic.ai.processor.env.EnvService;
import ai.metaheuristic.ai.processor.function.ProcessorFunctionService;
import ai.metaheuristic.ai.processor.utils.MetadataUtils;
import ai.metaheuristic.ai.utils.asset.AssetFile;
import ai.metaheuristic.ai.utils.asset.AssetUtils;
import ai.metaheuristic.ai.yaml.communication.keep_alive.KeepAliveRequestParamYaml;
import ai.metaheuristic.ai.yaml.communication.keep_alive.KeepAliveResponseParamYaml;
import ai.metaheuristic.ai.yaml.dispatcher_lookup.DispatcherLookupParamsYaml;
import ai.metaheuristic.ai.yaml.metadata.MetadataParamsYaml;
import ai.metaheuristic.ai.yaml.metadata.MetadataParamsYamlUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.utils.FileSystemUtils;
import ai.metaheuristic.commons.utils.checksum.CheckSumAndSignatureStatus;
import ai.metaheuristic.commons.utils.checksum.ChecksumWithSignatureUtils;
import lombok.AllArgsConstructor;
import lombok.Data;
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
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static ai.metaheuristic.ai.processor.ProcessorAndCoreData.*;
import static ai.metaheuristic.api.data.checksum_signature.ChecksumAndSignatureData.ChecksumWithSignature;
import static ai.metaheuristic.api.data.checksum_signature.ChecksumAndSignatureData.ChecksumWithSignatureInfo;

@SuppressWarnings("UnusedReturnValue")
@Service
@Slf4j
@Profile("processor")
@RequiredArgsConstructor
//@DependsOn({"Globals"})
public class MetadataService {

    private final ApplicationContext appCtx;
    private final Globals globals;
    private final EnvService envService;
    private final DispatcherLookupExtendedService dispatcherLookupExtendedService;
    private final ProcessorFunctionService processorFunctionService;

    private MetadataParamsYaml metadata = null;

    @Data
    @AllArgsConstructor
    public static class FunctionConfigAndStatus {
        @Nullable
        public final TaskParamsYaml.FunctionConfig functionConfig;
        @Nullable
        public final MetadataParamsYaml.Function status;
        @Nullable
        public final AssetFile assetFile;
        public final boolean contentIsInline;

        public FunctionConfigAndStatus(@Nullable MetadataParamsYaml.Function status) {
            this.functionConfig = null;
            this.assetFile = null;
            this.contentIsInline = false;
            this.status = status;
        }

        public FunctionConfigAndStatus(@Nullable TaskParamsYaml.FunctionConfig functionConfig, @Nullable MetadataParamsYaml.Function setFunctionState, AssetFile assetFile) {
            this.functionConfig = functionConfig;
            this.status = setFunctionState;
            this.assetFile = assetFile;
            this.contentIsInline = false;
        }
    }

    private static class MetadataSync {}
    private static final MetadataSync syncObj = new MetadataSync();

    @PostConstruct
    public void init() {
        final File metadataFile = new File(globals.processor.dir.dir, Consts.METADATA_YAML_FILE_NAME);
        if (metadataFile.exists()) {
            initMetadataFromFile(metadataFile);
        }
        else {
            final File metadataBackupFile = new File(globals.processor.dir.dir, Consts.METADATA_YAML_BAK_FILE_NAME);
            if (metadataBackupFile.exists()) {
                initMetadataFromFile(metadataBackupFile);
            }
        }
        if (metadata==null) {
            metadata = new MetadataParamsYaml();
        }
        fixProcessorCodes();
        for (ProcessorData.ProcessorCodeAndIdAndDispatcherUrlRef ref : getAllEnabledRefs()) {
            processorStateByDispatcherUrl(ref);
        }
        resetAllQuotas();
        markAllAsUnverified();
        updateMetadataFile();
        //noinspection unused
        int i=0;
    }

    private void initMetadataFromFile(File metadataFile) {
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

    private void fixProcessorCodes() {
        final List<String> codes = envService.getEnvParamsYaml().cores.stream().map(o -> o.code).collect(Collectors.toList());

        MetadataUtils.fixProcessorCodes(codes, metadata.procesorSessions);
    }

    public static ChecksumWithSignatureInfo prepareChecksumWithSignature(TaskParamsYaml.FunctionConfig functionConfig) {

        ChecksumWithSignatureInfo checksumWithSignatureInfo = new ChecksumWithSignatureInfo();

        // check requirements of signature
        if (functionConfig.checksumMap==null) {
            return checksumWithSignatureInfo;
        }

        // at 2020-09-02, only HashAlgo.SHA256WithSignature is supported for signing
        Map.Entry<EnumsApi.HashAlgo, String> entry = functionConfig.checksumMap.entrySet().stream()
                .filter(o -> o.getKey() == EnumsApi.HashAlgo.SHA256WithSignature)
                .findFirst()
                .orElse(null);

        if (entry==null || S.b(entry.getValue())) {
            entry = functionConfig.checksumMap.entrySet().stream().findFirst().orElse(null);
        }

        if (entry==null || S.b(entry.getValue())) {
            // both checksum and signature are null
            return checksumWithSignatureInfo;
        }

        ChecksumWithSignature checksumWithSignature = ChecksumWithSignatureUtils.parse(entry.getValue());
        checksumWithSignatureInfo.set(checksumWithSignature, entry.getValue(), entry.getKey());

        return checksumWithSignatureInfo;
    }

    private void markAllAsUnverified() {
        List<MetadataParamsYaml.Function> forRemoving = new ArrayList<>();
        for (MetadataParamsYaml.Function status : metadata.functions) {
            if (status.sourcing!= EnumsApi.FunctionSourcing.dispatcher) {
                continue;
            }
            if (status.state == EnumsApi.FunctionState.not_found) {
                forRemoving.add(status);
            }
            status.state = EnumsApi.FunctionState.none;
            status.checksum = EnumsApi.ChecksumState.not_yet;
            status.signature = EnumsApi.SignatureState.not_yet;
        }
        for (MetadataParamsYaml.Function status : forRemoving) {
            removeFunction(status.assetManagerUrl, status.code);
        }
    }

    @Nullable
    public FunctionConfigAndStatus syncFunctionStatus(AssetManagerUrl assetManagerUrl, DispatcherLookupParamsYaml.AssetManager assetManager, final String functionCode) {
        try {
            return syncFunctionStatusInternal(assetManagerUrl, assetManager, functionCode);
        } catch (Throwable th) {
            log.error("#815.080 Error in syncFunctionStatus()", th);
            return new FunctionConfigAndStatus(setFunctionState(assetManagerUrl, functionCode, EnumsApi.FunctionState.io_error));
        }
    }

    @Nullable
    private FunctionConfigAndStatus syncFunctionStatusInternal(AssetManagerUrl assetManagerUrl, DispatcherLookupParamsYaml.AssetManager assetManager, String functionCode) {
        MetadataParamsYaml.Function status = getFunctionDownloadStatuses(assetManagerUrl, functionCode);

        if (status == null) {
            return null;
        }
        if (status.sourcing!= EnumsApi.FunctionSourcing.dispatcher) {
            log.warn("#811.010 Function {} can't be downloaded from {} because a sourcing isn't 'dispatcher'.", functionCode, assetManagerUrl.url);
            return null;
        }
        if (status.state == EnumsApi.FunctionState.ready) {
            if (status.checksum!= EnumsApi.ChecksumState.not_yet && status.signature!= EnumsApi.SignatureState.not_yet) {
                return new FunctionConfigAndStatus(status);
            }
            setFunctionState(assetManagerUrl, functionCode, EnumsApi.FunctionState.none);
        }

        ProcessorFunctionService.DownloadedFunctionConfigStatus downloadedFunctionConfigStatus =
                processorFunctionService.downloadFunctionConfig(assetManager, functionCode);

        if (downloadedFunctionConfigStatus.status == ProcessorFunctionService.ConfigStatus.error) {
            return new FunctionConfigAndStatus(setFunctionState(assetManagerUrl, functionCode, EnumsApi.FunctionState.function_config_error));
        }
        if (downloadedFunctionConfigStatus.status == ProcessorFunctionService.ConfigStatus.not_found) {
            removeFunction(assetManagerUrl, functionCode);
            return null;
        }
        TaskParamsYaml.FunctionConfig functionConfig = downloadedFunctionConfigStatus.functionConfig;
        setChecksumMap(assetManagerUrl, functionCode, functionConfig.checksumMap);

        if (S.b(functionConfig.file) && S.b(functionConfig.content)) {
            log.error("#815.100 name of file for function {} is blank and content of function is blank too", functionCode);
            return new FunctionConfigAndStatus(setFunctionState(assetManagerUrl, functionCode, EnumsApi.FunctionState.function_config_error));
        }

        if (!S.b(functionConfig.content)) {
            return new FunctionConfigAndStatus(downloadedFunctionConfigStatus.functionConfig, setFunctionState(assetManagerUrl, functionCode, EnumsApi.FunctionState.ok), null, true);
        }

        File baseFunctionDir = prepareBaseDir(assetManagerUrl);

        final AssetFile assetFile = AssetUtils.prepareFunctionFile(baseFunctionDir, status.code, functionConfig.file);
        if (assetFile.isError) {
            return new FunctionConfigAndStatus(setFunctionState(assetManagerUrl, functionCode, EnumsApi.FunctionState.asset_error));
        }
        if (!assetFile.isContent) {
            return new FunctionConfigAndStatus(downloadedFunctionConfigStatus.functionConfig, setFunctionState(assetManagerUrl, functionCode, EnumsApi.FunctionState.none), assetFile);
        }

        return new FunctionConfigAndStatus(downloadedFunctionConfigStatus.functionConfig, setFunctionState(assetManagerUrl, functionCode, EnumsApi.FunctionState.ok), assetFile);
    }

/*
    @Nullable
    public DispatcherUrl findDispatcherByCode(ProcessorData.ProcessorCodeAndIdAndDispatcherUrlRef ref, String code) {
        synchronized (syncObj) {
            MetadataParamsYaml.Processor p = metadata.processors.get(ref.processorCode);
            if (p==null) {
                return null;
            }
            for (Map.Entry<String, MetadataParamsYaml.ProcessorSession> entry : p.states.entrySet()) {
                if (code.equals(entry.getValue().dispatcherCode)) {
                    return new DispatcherUrl(entry.getKey());
                }
            }
            return null;
        }
    }
*/

    public MetadataParamsYaml.ProcessorSession processorStateByDispatcherUrl(ProcessorData.ProcessorCodeAndIdAndDispatcherUrlRef ref) {
        synchronized (syncObj) {
            MetadataParamsYaml.ProcessorSession processorState = getProcessorSession(ref.dispatcherUrl.url);
            // fix for wrong metadata.yaml data
            if (processorState.dispatcherCode == null) {
                processorState.dispatcherCode = asEmptyProcessorState(ref.dispatcherUrl).dispatcherCode;
                updateMetadataFile();
            }
            return processorState;
        }
    }

    public void registerTaskQuota(String dispatcherUrl, Long taskId, @Nullable String tag, int quota) {
        synchronized (syncObj) {
            MetadataParamsYaml.ProcessorSession ps = getProcessorSession(dispatcherUrl);
            MetadataParamsYaml.Quota q = null;
            for (MetadataParamsYaml.Quota o1 : ps.quotas) {
                if (o1.taskId.equals(taskId)) {
                    q = o1;
                    break;
                }
            }
            if (q!=null) {
                q.tag=tag;
                q.quota=quota;
            }
            else {
                ps.quotas.add(new MetadataParamsYaml.Quota(taskId, tag, quota));
            }
            updateMetadataFile();
        }
    }

    // must be sync outside
    private MetadataParamsYaml.ProcessorSession getProcessorSession(String dispatcherUrl) {
        MetadataParamsYaml.ProcessorSession ps = metadata.procesorSessions.get(dispatcherUrl);
        if (ps==null) {
            final String es = "dispatcherUrl isn't registered in metadata, " + dispatcherUrl;
            log.error(es);
            throw new IllegalStateException(es);
        }
        return ps;
    }

    public void resetAllQuotas() {
        synchronized (syncObj) {
            metadata.procesorSessions.forEach((k,v)->v.quotas.clear());
            updateMetadataFile();
        }
    }

    public int currentQuota(String dispatcherUrl) {
        synchronized (syncObj) {
            MetadataParamsYaml.ProcessorSession ps = getProcessorSession(dispatcherUrl);
            int sum = 0;
            for (MetadataParamsYaml.Quota o : ps.quotas) {
                int quota = o.quota;
                sum += quota;
            }
            return sum;
        }
    }

    public void removeQuota(String dispatcherUrl, Long taskId) {
        synchronized (syncObj) {
            MetadataParamsYaml.ProcessorSession ps = getProcessorSession(dispatcherUrl);
            for (MetadataParamsYaml.Quota o : ps.quotas) {
                if (o.taskId.equals(taskId)) {
                    ps.quotas.remove(o);
                    break;
                }
            }
            updateMetadataFile();
        }
    }

    public List<String> getProcessorCodes() {
        synchronized (syncObj) {
            return new ArrayList<>(metadata.processors.keySet());
        }
    }

    public Set<ProcessorData.ProcessorCodeAndIdAndDispatcherUrlRef> getAllEnabledRefsForDispatcherUrl(DispatcherUrl dispatcherUrl) {
        return getAllRefs( (du) -> {
            if (du.equals(dispatcherUrl)) {
                final DispatcherLookupExtendedService.DispatcherLookupExtended dispatcher = dispatcherLookupExtendedService.getDispatcher(dispatcherUrl);
                return dispatcher != null && !dispatcher.dispatcherLookup.disabled;
            }
            return false;
        } );
    }

    @Nullable
    public ProcessorData.ProcessorCodeAndIdAndDispatcherUrlRef getRefsForDispatcherUrl(DispatcherUrl dispatcherUrl) {
        final Set<ProcessorData.ProcessorCodeAndIdAndDispatcherUrlRef> set = getAllRefs((du) -> du.equals(dispatcherUrl));
        if (set.isEmpty()) {
            return null;
        }
        if (set.size()>1) {
            final String es = "metadata is broken. there are more than one ProcessorCodeAndIdAndDispatcherUrlRef for " + dispatcherUrl;
            log.error(es);
            throw new IllegalStateException(es);
        }
        return new ArrayList<>(set).get(0);
    }

    public Set<ProcessorData.ProcessorCodeAndIdAndDispatcherUrlRef> getAllRefs() {
        return getAllRefs( (dispatcherUrl) -> true );
    }

    public Set<ProcessorData.ProcessorCodeAndIdAndDispatcherUrlRef> getAllEnabledRefs() {
        return getAllRefs( (dispatcherUrl) -> {
            final DispatcherLookupExtendedService.DispatcherLookupExtended dispatcher = dispatcherLookupExtendedService.getDispatcher(dispatcherUrl);
            return dispatcher != null && !dispatcher.dispatcherLookup.disabled;
        } );
    }

    private Set<ProcessorData.ProcessorCodeAndIdAndDispatcherUrlRef> getAllRefs(Function<DispatcherUrl, Boolean> function) {
        synchronized (syncObj) {
            Set<ProcessorData.ProcessorCodeAndIdAndDispatcherUrlRef> refs = new HashSet<>();
            for (Map.Entry<String, MetadataParamsYaml.Processor> processorEntry : metadata.processors.entrySet()) {
                if (processorEntry.getValue()==null) {
                    continue;
                }
                for (Map.Entry<String, MetadataParamsYaml.ProcessorSession> stateEntry : processorEntry.getValue().states.entrySet()) {
                    final DispatcherUrl dispatcherUrl = new DispatcherUrl(stateEntry.getKey());
                    if (function.apply(dispatcherUrl)) {
                        refs.add( new ProcessorData.ProcessorCodeAndIdAndDispatcherUrlRef(
                                processorEntry.getKey(), stateEntry.getValue().processorId, dispatcherUrl));
                    }
                }
            }
            return refs;
        }
    }

    public MetadataParamsYaml.ProcessorSession getProcessorSession(DispatcherUrl dispatcherUrl) {
        synchronized (syncObj) {
            return getProcessorSession(dispatcherUrl.url);
        }
    }

    @Nullable
    public ProcessorData.ProcessorCodeAndIdAndDispatcherUrlRef getRef(String processorCode, DispatcherUrl dispatcherUrl) {
        synchronized (syncObj) {
            for (Map.Entry<String, MetadataParamsYaml.Processor> processorEntry : metadata.processors.entrySet()) {
                if (!processorCode.equals(processorEntry.getKey())) {
                    continue;
                }
                MetadataParamsYaml.ProcessorSession processorState = processorEntry.getValue().states.get(dispatcherUrl.url);
                return processorState==null ? null : new ProcessorData.ProcessorCodeAndIdAndDispatcherUrlRef(processorCode, processorState.processorId, dispatcherUrl);
            }
            return null;
        }
    }

    public void setProcessorIdAndSessionId(ProcessorData.ProcessorCodeAndIdAndDispatcherUrlRef ref, String processorId, String sessionId) {
        if (StringUtils.isBlank(processorId)) {
            throw new IllegalStateException("#815.180 processorId is null");
        }
        synchronized (syncObj) {
            final MetadataParamsYaml.ProcessorSession processorState = getProcessorSession(ref.dispatcherUrl.url);
            if (!Objects.equals(processorState.processorId, processorId) || !Objects.equals(processorState.sessionId, sessionId)) {
                processorState.processorId = processorId;
                processorState.sessionId = sessionId;
                updateMetadataFile();
            }
        }
    }

    public void deRegisterFunctionCode(ProcessorAndCoreData.AssetManagerUrl assetManagerUrl, String functionCode) {
        synchronized (syncObj) {
            metadata.functions.removeIf(next -> next.assetManagerUrl.equals(assetManagerUrl.url) && next.code.equals(functionCode));
        }
    }

    public List<MetadataParamsYaml.Function> registerNewFunctionCode(DispatcherUrl dispatcherUrl, List<KeepAliveResponseParamYaml.Functions.Info> infos) {
        final DispatcherLookupExtendedService.DispatcherLookupExtended dispatcher =
                dispatcherLookupExtendedService.lookupExtendedMap.get(dispatcherUrl);

        AssetManagerUrl assetManagerUrl = new AssetManagerUrl(dispatcher.dispatcherLookup.assetManagerUrl);

        synchronized (syncObj) {
            boolean isChanged = false;
            for (KeepAliveResponseParamYaml.Functions.Info info : infos) {
                MetadataParamsYaml.Function status = metadata.functions.stream()
                        .filter(o->o.assetManagerUrl.equals(assetManagerUrl.url) && o.code.equals(info.code))
                        .findFirst().orElse(null);
                if (status==null || status.state == EnumsApi.FunctionState.not_found) {
                    setFunctionDownloadStatusInternal(assetManagerUrl, info.code, info.sourcing, EnumsApi.FunctionState.none);
                    isChanged = true;
                }
            }

            // set state to FunctionState.not_found if function doesn't exist at Dispatcher any more
            for (MetadataParamsYaml.Function status : metadata.functions) {
                if (status.assetManagerUrl.equals(assetManagerUrl.url) && infos.stream().filter(i-> i.code.equals(status.code)).findFirst().orElse(null)==null) {
                    setFunctionDownloadStatusInternal(assetManagerUrl, status.code, status.sourcing, EnumsApi.FunctionState.not_found);
                    isChanged = true;
                }
            }
            if (isChanged) {
                updateMetadataFile();
            }
        }
        return metadata.functions;
    }

    @Nullable
    public MetadataParamsYaml.Function setFunctionState(final AssetManagerUrl assetManagerUrl, String functionCode, EnumsApi.FunctionState functionState) {
        if (S.b(functionCode)) {
            throw new IllegalStateException("#815.240 functionCode is null");
        }
        synchronized (syncObj) {
            MetadataParamsYaml.Function status = metadata.functions.stream().filter(o -> o.assetManagerUrl.equals(assetManagerUrl.url)).filter(o-> o.code.equals(functionCode)).findFirst().orElse(null);
            if (status == null) {
                return null;
            }
            status.state = functionState;
            if (status.state == EnumsApi.FunctionState.none||status.state == EnumsApi.FunctionState.ok) {
                status.checksum= EnumsApi.ChecksumState.not_yet;
                status.signature= EnumsApi.SignatureState.not_yet;
            }
            updateMetadataFile();
            return status;
        }
    }

    public void setFunctionFromProcessorAsReady(final AssetManagerUrl assetManagerUrl, String functionCode) {
        synchronized (syncObj) {
            if (S.b(functionCode)) {
                throw new IllegalStateException("#815.240 functionCode is null");
            }
            MetadataParamsYaml.Function status = metadata.functions.stream().filter(o -> o.assetManagerUrl.equals(assetManagerUrl.url)).filter(o-> o.code.equals(functionCode)).findFirst().orElse(null);
            if (status == null) {
                return;
            }
            status.state = EnumsApi.FunctionState.ready;
            status.checksum= EnumsApi.ChecksumState.runtime;
            status.signature= EnumsApi.SignatureState.runtime;

            updateMetadataFile();
        }
    }

    public void setChecksumMap(final AssetManagerUrl assetManagerUrl, String functionCode, @Nullable Map<EnumsApi.HashAlgo, String> checksumMap) {
        if (S.b(functionCode)) {
            throw new IllegalStateException("#815.240 functionCode is null");
        }
        if (checksumMap==null) {
            return;
        }

        synchronized (syncObj) {
            MetadataParamsYaml.Function status = metadata.functions.stream().filter(o -> o.assetManagerUrl.equals(assetManagerUrl.url)).filter(o-> o.code.equals(functionCode)).findFirst().orElse(null);
            if (status == null) {
                return;
            }
            status.checksumMap.putAll(checksumMap);
            updateMetadataFile();
        }
    }

    public void setChecksumAndSignatureStatus(final AssetManagerUrl assetManagerUrl, String functionCode, CheckSumAndSignatureStatus checkSumAndSignatureStatus) {
        if (S.b(functionCode)) {
            throw new IllegalStateException("#815.240 functionCode is null");
        }
        synchronized (syncObj) {
            MetadataParamsYaml.Function status = metadata.functions.stream().filter(o -> o.assetManagerUrl.equals(assetManagerUrl.url)).filter(o-> o.code.equals(functionCode)).findFirst().orElse(null);
            if (status == null) {
                return;
            }
            status.checksum = checkSumAndSignatureStatus.checksum;
            status.signature = checkSumAndSignatureStatus.signature;
            updateMetadataFile();
        }
    }

    private boolean removeFunction(final AssetManagerUrl assetManagerUrl, String functionCode) {
        return removeFunction(assetManagerUrl.url, functionCode);
    }

    private boolean removeFunction(final String assetManagerUrl, String functionCode) {
        if (S.b(functionCode)) {
            throw new IllegalStateException("#815.280 functionCode is empty");
        }
        synchronized (syncObj) {
            List<MetadataParamsYaml.Function> statuses = metadata.functions.stream().filter(o->!(o.assetManagerUrl.equals(assetManagerUrl) && o.code.equals(functionCode))).collect(Collectors.toList());
            if (statuses.size()!= metadata.functions.size()) {
                metadata.functions.clear();
                metadata.functions.addAll(statuses);
                updateMetadataFile();
            }
            return true;
        }
    }

    public void setFunctionDownloadStatus(final AssetManagerUrl assetManagerUrl, String functionCode, EnumsApi.FunctionSourcing sourcing, EnumsApi.FunctionState functionState) {
        if (S.b(functionCode)) {
            throw new IllegalStateException("#815.360 functionCode is empty");
        }
        synchronized (syncObj) {
            setFunctionDownloadStatusInternal(assetManagerUrl, functionCode, sourcing, functionState);
            updateMetadataFile();
        }
    }

    // it could be null if this function was deleted
    @Nullable
    public MetadataParamsYaml.Function getFunctionDownloadStatuses(AssetManagerUrl assetManagerUrl, String functionCode) {
        synchronized (syncObj) {
            return metadata.functions.stream()
                    .filter(o->o.assetManagerUrl.equals(assetManagerUrl.url) && o.code.equals(functionCode))
                    .findFirst().orElse(null);
        }
    }

    public List<KeepAliveRequestParamYaml.FunctionDownloadStatuses.Status> getAsFunctionDownloadStatuses(final AssetManagerUrl assetManagerUrl) {
        synchronized (syncObj) {
            return metadata.functions.stream()
                    .filter(o->o.assetManagerUrl.equals(assetManagerUrl.url))
                    .map(o->new KeepAliveRequestParamYaml.FunctionDownloadStatuses.Status(o.code, o.state))
                    .collect(Collectors.toList());
        }
    }

    private void setFunctionDownloadStatusInternal(AssetManagerUrl assetManagerUrl, String code, EnumsApi.FunctionSourcing sourcing, EnumsApi.FunctionState functionState) {
        MetadataParamsYaml.Function status = metadata.functions.stream().filter(o->o.assetManagerUrl.equals(assetManagerUrl.url) && o.code.equals(code)).findFirst().orElse(null);
        if (status == null) {
            status = new MetadataParamsYaml.Function(EnumsApi.FunctionState.none, code, assetManagerUrl.url, sourcing, EnumsApi.ChecksumState.not_yet, EnumsApi.SignatureState.not_yet);
            metadata.functions.add(status);
        }
        status.state = functionState;
    }

    public List<MetadataParamsYaml.Function> getStatuses() {
        synchronized (syncObj) {
            return Collections.unmodifiableList(metadata.functions);
        }
    }

    @SuppressWarnings("unused")
    public void setSessionId(DispatcherUrl dispatcherUrl, String sessionId) {
        if (StringUtils.isBlank(sessionId)) {
            throw new IllegalStateException("#815.400 sessionId is null");
        }
        synchronized (syncObj) {
            getProcessorSession(dispatcherUrl.url).processorId = sessionId;
            updateMetadataFile();
        }
    }

    private void updateMetadataFile() {
        final File metadataFile =  new File(globals.processor.dir.dir, Consts.METADATA_YAML_FILE_NAME);
        if (metadataFile.exists()) {
            log.trace("#815.420 Metadata file exists. Make backup");
            File yamlFileBak = new File(globals.processor.dir.dir, Consts.METADATA_YAML_BAK_FILE_NAME);
            //noinspection ResultOfMethodCallIgnored
            yamlFileBak.delete();
            //noinspection ResultOfMethodCallIgnored
            metadataFile.renameTo(yamlFileBak);
        }

        try {
            String data = MetadataParamsYamlUtils.BASE_YAML_UTILS.toString(metadata);
            FileSystemUtils.writeStringToFileWithSync(metadataFile, data, StandardCharsets.UTF_8);
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
            File yamlFileBak = new File(globals.processor.dir.dir, Consts.METADATA_YAML_BAK_FILE_NAME);
            String content = FileUtils.readFileToString(yamlFileBak, StandardCharsets.UTF_8);
            File yamlFile = new File(globals.processor.dir.dir, Consts.METADATA_YAML_FILE_NAME);
            FileSystemUtils.writeStringToFileWithSync(yamlFile, content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("#815.500 restoring of metadata.yaml from backup was failed. Processor will be stopped.");
            System.exit(SpringApplication.exit(appCtx, () -> -500));
        }

    }

    private static MetadataParamsYaml.ProcessorSession asEmptyProcessorState(DispatcherUrl dispatcherUrl) {
        MetadataParamsYaml.ProcessorSession processorState = new MetadataParamsYaml.ProcessorSession();
        processorState.dispatcherCode = asCode(dispatcherUrl);
        return processorState;
    }

    public static String asCode(CommonUrl commonUrl) {
        String s = commonUrl.getUrl().toLowerCase();
        if (s.startsWith(Consts.HTTP)) {
            s = s.substring(Consts.HTTP.length());
        }
        else if (s.startsWith(Consts.HTTPS)) {
            s = s.substring(Consts.HTTPS.length());
        }
        s = StringUtils.replaceEach(s, new String[]{".", ":", "/"}, new String[]{"_", "-", "-"});
        return s;
    }

    public File prepareBaseDir(AssetManagerUrl assetManagerUrl) {
        final File dir = new File(globals.processorResourcesDir, asCode(assetManagerUrl));
        if (!dir.exists()) {
            //noinspection unused
            boolean status = dir.mkdirs();
        }
        return dir;
    }


}
