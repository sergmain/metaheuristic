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
import ai.metaheuristic.ai.processor.data.ProcessorData;
import ai.metaheuristic.ai.yaml.dispatcher_lookup.DispatcherLookupExtendedParams;
import ai.metaheuristic.ai.yaml.metadata.MetadataParamsYaml;
import ai.metaheuristic.ai.yaml.metadata.MetadataParamsYamlUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.utils.checksum.ChecksumWithSignatureUtils;
import ai.metaheuristic.commons.yaml.task.TaskParamsYaml;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.lang.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;
import java.util.stream.Collectors;

import static ai.metaheuristic.ai.processor.ProcessorAndCoreData.*;
import static ai.metaheuristic.ai.processor.data.ProcessorData.ProcessorCodeAndIdAndDispatcherUrlRef;
import static ai.metaheuristic.ai.processor.data.ProcessorData.ProcessorCoreAndProcessorIdAndDispatcherUrlRef;
import static ai.metaheuristic.api.data.checksum_signature.ChecksumAndSignatureData.ChecksumWithSignature;
import static ai.metaheuristic.api.data.checksum_signature.ChecksumAndSignatureData.ChecksumWithSignatureInfo;

@SuppressWarnings("UnusedReturnValue")
@Slf4j
public class MetadataParams {

    private final EnvParams envParams;
    private final DispatcherLookupExtendedParams dispatcherLookupExtendedService;
    private final Path processorPath;
    private final Path processorResourcesPath;

    private MetadataParamsYaml metadata = null;

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final ReentrantReadWriteLock.ReadLock readLock = lock.readLock();
    private final ReentrantReadWriteLock.WriteLock writeLock = lock.writeLock();

    @SneakyThrows
    public MetadataParams(Path processorPath, EnvParams envParams, DispatcherLookupExtendedParams dispatcherLookupExtendedService) {
        this.processorPath = processorPath;
        this.processorResourcesPath = processorPath.resolve(Consts.RESOURCES_DIR);
        Files.createDirectories(processorResourcesPath);

        this.envParams = envParams;
        this.dispatcherLookupExtendedService = dispatcherLookupExtendedService;

        final Path metadataFile = processorPath.resolve(Consts.METADATA_YAML_FILE_NAME);
        if (Files.exists(metadataFile)) {
            initMetadataFromFile(metadataFile);
        }
        if (metadata==null) {
            final Path metadataBackupFile = processorPath.resolve(Consts.METADATA_YAML_BAK_FILE_NAME);
            if (Files.exists(metadataBackupFile)) {
                initMetadataFromFile(metadataBackupFile);
            }
        }
        if (metadata==null) {
            metadata = new MetadataParamsYaml();
        }
        fixDispatcherUrls();
        fixProcessorCodes();
        for (ProcessorCodeAndIdAndDispatcherUrlRef ref : getAllEnabledRefs()) {
            processorStateByDispatcherUrl(ref);
        }
        resetAllQuotas();

        //noinspection unused
        int i=0;
    }

    public static void fixProcessorCodes(List<String> codes, Map<String, MetadataParamsYaml.ProcessorSession> map) {
        Set<String> forDeletion = new HashSet<>();
        for (Map.Entry<String, MetadataParamsYaml.ProcessorSession> entry : map.entrySet()) {
            final LinkedHashMap<String, Long> cores = entry.getValue().cores;
            for (String key : cores.keySet()) {
                if (!codes.contains(key)) {
                    forDeletion.add(key);
                }
            }
            forDeletion.forEach(cores::remove);

            for (String code : codes) {
                if (!cores.containsKey(code)) {
                    cores.put(code, null);
                }
            }
        }
    }

    private void fixDispatcherUrls() {
        List<DispatcherUrl> dispatcherUrls = dispatcherLookupExtendedService.getAllEnabledDispatchers();
        for (DispatcherUrl dispatcherUrl : dispatcherUrls) {
            metadata.processorSessions.computeIfAbsent(dispatcherUrl.url, (o)->new MetadataParamsYaml.ProcessorSession(asCode(dispatcherUrl), null, null));
        }
    }

    private void initMetadataFromFile(Path metadataFile) {
        try {
            String yaml = Files.readString(metadataFile, StandardCharsets.UTF_8);
            metadata = MetadataParamsYamlUtils.BASE_YAML_UTILS.to(yaml);
        } catch (org.yaml.snakeyaml.reader.ReaderException e) {
            log.error("815.020 Bad data in " + metadataFile.toAbsolutePath());
        } catch (Throwable e) {
            log.error("815.040 Error", e);
        }
    }

    private void fixProcessorCodes() {
        final List<String> codes = envParams.getEnvParamsYaml().cores.stream().map(o -> o.code).collect(Collectors.toList());

        fixProcessorCodes(codes, metadata.processorSessions);
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

    public MetadataParamsYaml.ProcessorSession processorStateByDispatcherUrl(ProcessorCodeAndIdAndDispatcherUrlRef ref) {
        try {
            writeLock.lock();
            MetadataParamsYaml.ProcessorSession processorState = metadata.processorSessions.get(ref.dispatcherUrl.url);
            // fix for wrong metadata.yaml data
            if (processorState.dispatcherCode == null) {
                processorState.dispatcherCode = asEmptyProcessorState(ref.dispatcherUrl).dispatcherCode;
                updateMetadataFile();
            }
            return processorState;
        } finally {
            writeLock.unlock();
        }
    }

    public MetadataParamsYaml.ProcessorSession processorStateByDispatcherUrl(ProcessorCoreAndProcessorIdAndDispatcherUrlRef ref) {
        try {
            readLock.lock();
            MetadataParamsYaml.ProcessorSession processorState = getProcessorSession(ref.dispatcherUrl.url);
            // fix for wrong metadata.yaml data
            if (processorState.dispatcherCode == null) {
                try {
                    readLock.unlock();
                    writeLock.lock();
                    processorState.dispatcherCode = asEmptyProcessorState(ref.dispatcherUrl).dispatcherCode;
                    updateMetadataFile();
                } finally {
                    writeLock.unlock();
                    readLock.lock();
                }
            }
            return processorState;
        } finally {
            readLock.unlock();
        }
    }

    public void registerTaskQuota(String dispatcherUrl, Long taskId, @Nullable String tag, int quota) {
        try {
            writeLock.lock();
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
        } finally {
            writeLock.unlock();
        }
    }

    // must be sync outside
    private MetadataParamsYaml.ProcessorSession getProcessorSession(String dispatcherUrl) {
        MetadataParamsYaml.ProcessorSession ps = metadata.processorSessions.get(dispatcherUrl);
        if (ps==null) {
            final String es = "dispatcherUrl isn't registered in metadata, " + dispatcherUrl;
            log.error(es);
            throw new IllegalStateException(es);
        }
        return ps;
    }

    public void resetAllQuotas() {
        try {
            writeLock.lock();
            metadata.processorSessions.forEach((k, v)->v.quotas.clear());
            updateMetadataFile();
        } finally {
            writeLock.unlock();
        }
    }

    public int currentQuota(String dispatcherUrl) {
        try {
            readLock.lock();
            MetadataParamsYaml.ProcessorSession ps = getProcessorSession(dispatcherUrl);
            int sum = 0;
            for (MetadataParamsYaml.Quota o : ps.quotas) {
                int quota = o.quota;
                sum += quota;
            }
            return sum;
        } finally {
            readLock.unlock();
        }
    }

    public void removeQuota(String dispatcherUrl, Long taskId) {
        try {
            writeLock.lock();
            MetadataParamsYaml.ProcessorSession ps = getProcessorSession(dispatcherUrl);
            for (MetadataParamsYaml.Quota o : ps.quotas) {
                if (o.taskId.equals(taskId)) {
                    ps.quotas.remove(o);
                    break;
                }
            }
            updateMetadataFile();
        } finally {
            writeLock.unlock();
        }
    }

    public Set<ProcessorData.ProcessorCoreAndProcessorIdAndDispatcherUrlRef> getAllCoresForDispatcherUrl(DispatcherUrl dispatcherUrl) {
        final Set<ProcessorData.ProcessorCoreAndProcessorIdAndDispatcherUrlRef> set = getAllRefsForCores((du) -> du.equals(dispatcherUrl));
        return set;
    }

    public Set<ProcessorCoreAndProcessorIdAndDispatcherUrlRef> getAllEnabledRefsForCores() {
        return getAllRefsForCores(this::selectEnabledDispathcersFunc);
    }

    public Set<ProcessorData.ProcessorCodeAndIdAndDispatcherUrlRef> getAllEnabledRefs() {
        return getAllRefs(this::selectEnabledDispathcersFunc);
    }

    private boolean selectEnabledDispathcersFunc(DispatcherUrl dispatcherUrl) {
        final DispatcherLookupExtendedParams.DispatcherLookupExtended dispatcher = dispatcherLookupExtendedService.getDispatcher(dispatcherUrl);
        return dispatcher != null && !dispatcher.dispatcherLookup.disabled;
    }

    private Set<ProcessorData.ProcessorCodeAndIdAndDispatcherUrlRef> getAllRefs(Function<DispatcherUrl, Boolean> function) {
        try {
            readLock.lock();
            Set<ProcessorData.ProcessorCodeAndIdAndDispatcherUrlRef> refs = new HashSet<>();
            for (Map.Entry<String, MetadataParamsYaml.ProcessorSession> processorEntry : metadata.processorSessions.entrySet()) {
                final MetadataParamsYaml.ProcessorSession processorSession = processorEntry.getValue();
                if (processorSession ==null) {
                    continue;
                }
                final DispatcherUrl dispatcherUrl = new DispatcherUrl(processorEntry.getKey());
                if (function.apply(dispatcherUrl)) {
                    refs.add( new ProcessorData.ProcessorCodeAndIdAndDispatcherUrlRef(
                            dispatcherUrl, asCode(dispatcherUrl), processorSession.processorId));
                }
            }
            return Collections.unmodifiableSet(refs);
        } finally {
            readLock.unlock();
        }
    }

    private Set<ProcessorCoreAndProcessorIdAndDispatcherUrlRef> getAllRefsForCores(Function<DispatcherUrl, Boolean> function) {
        try {
            readLock.lock();
            Set<ProcessorCoreAndProcessorIdAndDispatcherUrlRef> refs = new HashSet<>();
            for (Map.Entry<String, MetadataParamsYaml.ProcessorSession> processorEntry : metadata.processorSessions.entrySet()) {
                final MetadataParamsYaml.ProcessorSession processorSession = processorEntry.getValue();
                if (processorSession ==null) {
                    continue;
                }
                for (Map.Entry<String, Long> coreEntry : processorSession.cores.entrySet()) {
                    final DispatcherUrl dispatcherUrl = new DispatcherUrl(processorEntry.getKey());
                    if (function.apply(dispatcherUrl)) {
                        refs.add( new ProcessorCoreAndProcessorIdAndDispatcherUrlRef(
                                dispatcherUrl, asCode(dispatcherUrl), processorSession.processorId, coreEntry.getKey(), coreEntry.getValue()));
                    }
                }
            }
            return Collections.unmodifiableSet(refs);
        } finally {
            readLock.unlock();
        }
    }

    public MetadataParamsYaml.ProcessorSession getProcessorSession(DispatcherUrl dispatcherUrl) {
        try {
            readLock.lock();
            return getProcessorSession(dispatcherUrl.url);
        } finally {
            readLock.unlock();
        }
    }

    @Nullable
    public ProcessorData.ProcessorCoreAndProcessorIdAndDispatcherUrlRef getCoreRef(String coreCode, DispatcherUrl dispatcherUrl) {
        try {
            readLock.lock();
            MetadataParamsYaml.ProcessorSession processorState = getProcessorSession(dispatcherUrl.url);
            for (Map.Entry<String, Long> core : processorState.cores.entrySet()) {
                if (!coreCode.equals(core.getKey())) {
                    continue;
                }
                return new ProcessorData.ProcessorCoreAndProcessorIdAndDispatcherUrlRef(
                        dispatcherUrl, processorState.dispatcherCode, processorState.processorId, coreCode, core.getValue());
            }
            return null;
        } finally {
            readLock.unlock();
        }
    }

    @Nullable
    public ProcessorData.ProcessorCodeAndIdAndDispatcherUrlRef getRef(DispatcherUrl dispatcherUrl) {
        try {
            readLock.lock();
            MetadataParamsYaml.ProcessorSession processorState = getProcessorSession(dispatcherUrl.url);
            return new ProcessorData.ProcessorCodeAndIdAndDispatcherUrlRef(
                    dispatcherUrl, processorState.dispatcherCode, processorState.processorId);
        } finally {
            readLock.unlock();
        }
    }

    public void setProcessorIdAndSessionId(DispatcherUrl dispatcherUrl, String processorIdStr, String sessionId) {
        if (StringUtils.isBlank(processorIdStr)) {
            throw new IllegalStateException("815.180 processorId is null");
        }
        Long processorId = Long.parseLong(processorIdStr);

        try {
            writeLock.lock();
            final MetadataParamsYaml.ProcessorSession processorState = getProcessorSession(dispatcherUrl.url);
            if (!Objects.equals(processorState.processorId, processorId) || !Objects.equals(processorState.sessionId, sessionId)) {
                processorState.processorId = processorId;
                processorState.sessionId = sessionId;
                updateMetadataFile();
            }
        } finally {
            writeLock.unlock();
        }
    }

    public void setCoreId(DispatcherUrl dispatcherUrl, String coreCode, Long coreId) {
        try {
            writeLock.lock();
            final MetadataParamsYaml.ProcessorSession processorState = getProcessorSession(dispatcherUrl.url);
            processorState.cores.put(coreCode, coreId);
            updateMetadataFile();
        } finally {
            writeLock.unlock();
        }
    }

    @SuppressWarnings("unused")
    public void setSessionId(DispatcherUrl dispatcherUrl, String sessionId) {
        if (StringUtils.isBlank(sessionId)) {
            throw new IllegalStateException("815.400 sessionId is null");
        }
        try {
            writeLock.lock();
            getProcessorSession(dispatcherUrl.url).sessionId = sessionId;
            updateMetadataFile();
        } finally {
            writeLock.unlock();
        }
    }

    @SneakyThrows
    private void updateMetadataFile() {
        final Path metadataFile =  processorPath.resolve(Consts.METADATA_YAML_FILE_NAME);
        if (Files.exists(metadataFile)) {
            log.trace("815.420 Metadata file exists. Make backup");
            Path yamlFileBak = processorPath.resolve(Consts.METADATA_YAML_BAK_FILE_NAME);
            Files.deleteIfExists(yamlFileBak);
            Files.move(metadataFile, yamlFileBak);
        }

        try {
            String data = MetadataParamsYamlUtils.BASE_YAML_UTILS.toString(metadata);
            Files.writeString(metadataFile, data, StandardCharsets.UTF_8);
            String check = Files.readString(metadataFile, StandardCharsets.UTF_8);
            if (!check.equals(data)) {
                log.error("815.440 Metadata was persisted with an error, content is different, size - expected: {}, actual: {}, Processor will be closed", data.length(), check.length());
                throw new TerminateApplicationException();
            }
        } catch (Throwable th) {
            log.error("815.460 Fatal error, Processor will be closed", th);
            throw new TerminateApplicationException();
        }
    }

    private void restoreFromBackup() {
        log.info("815.480 Trying to restore previous state of metadata.yaml");
        try {
            Path yamlFileBak = processorPath.resolve(Consts.METADATA_YAML_BAK_FILE_NAME);
            String content = Files.readString(yamlFileBak, StandardCharsets.UTF_8);
            Path yamlFile = processorPath.resolve(Consts.METADATA_YAML_FILE_NAME);
            Files.writeString(yamlFile, content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("815.500 restoring of metadata.yaml from backup was failed. Processor will be stopped.");
            throw new TerminateApplicationException();
        }
    }

    private static MetadataParamsYaml.ProcessorSession asEmptyProcessorState(DispatcherUrl dispatcherUrl) {
        MetadataParamsYaml.ProcessorSession processorState = new MetadataParamsYaml.ProcessorSession();
        processorState.dispatcherCode = asCode(dispatcherUrl);
        return processorState;
    }

    public static String asCode(CommonUrl commonUrl) {
        return asCode(commonUrl.getUrl().toLowerCase());
    }

    public static String asCode(String url) {
        String s = url;
        if (s.startsWith(Consts.HTTP)) {
            s = s.substring(Consts.HTTP.length());
        }
        else if (s.startsWith(Consts.HTTPS)) {
            s = s.substring(Consts.HTTPS.length());
        }
        s = StringUtils.replaceEach(s, new String[]{".", ":", "/"}, new String[]{"_", "-", "-"});
        return s;
    }

    @SneakyThrows
    public Path prepareBaseDir(AssetManagerUrl assetManagerUrl) {
        Path dir = processorResourcesPath.resolve(asCode(assetManagerUrl));
        if (Files.notExists(dir)) {
            //noinspection unused
            Files.createDirectories(dir);
        }
        return dir;
    }


}
