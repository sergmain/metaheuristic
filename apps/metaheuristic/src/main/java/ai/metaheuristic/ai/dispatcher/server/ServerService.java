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

package ai.metaheuristic.ai.dispatcher.server;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.dispatcher.beans.Processor;
import ai.metaheuristic.ai.exceptions.BinaryDataNotFoundException;
import ai.metaheuristic.ai.exceptions.VariableSavingException;
import ai.metaheuristic.ai.dispatcher.DispatcherCommandProcessor;
import ai.metaheuristic.ai.dispatcher.beans.Variable;
import ai.metaheuristic.ai.dispatcher.variable.VariableService;
import ai.metaheuristic.ai.dispatcher.repositories.IdsRepository;
import ai.metaheuristic.ai.dispatcher.repositories.ProcessorRepository;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.ai.dispatcher.repositories.ExecContextRepository;
import ai.metaheuristic.ai.dispatcher.function.FunctionDataService;
import ai.metaheuristic.ai.dispatcher.processor.ProcessorCache;
import ai.metaheuristic.ai.dispatcher.processor.ProcessorTopLevelService;
import ai.metaheuristic.ai.dispatcher.task.TaskPersistencer;
import ai.metaheuristic.ai.resource.AssetFile;
import ai.metaheuristic.ai.resource.ResourceUtils;
import ai.metaheuristic.ai.resource.ResourceWithCleanerInfo;
import ai.metaheuristic.ai.processor.sourcing.git.GitSourcingService;
import ai.metaheuristic.ai.utils.RestUtils;
import ai.metaheuristic.ai.yaml.communication.dispatcher.DispatcherCommParamsYaml;
import ai.metaheuristic.ai.yaml.communication.dispatcher.DispatcherCommParamsYamlUtils;
import ai.metaheuristic.ai.yaml.communication.processor.ProcessorCommParamsYaml;
import ai.metaheuristic.ai.yaml.communication.processor.ProcessorCommParamsYamlUtils;
import ai.metaheuristic.ai.yaml.processor_status.ProcessorStatusYaml;
import ai.metaheuristic.ai.yaml.processor_status.ProcessorStatusYamlUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.api.dispatcher.ExecContext;
import ai.metaheuristic.commons.utils.DirUtils;
import ai.metaheuristic.commons.yaml.task.TaskParamsYamlUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.FileSystemResource;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.util.MultiValueMap;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Service
@Profile("dispatcher")
@Slf4j
@RequiredArgsConstructor
public class ServerService {

    private static final UploadResult OK_UPLOAD_RESULT = new UploadResult(Enums.UploadResourceStatus.OK, null);
    public static final long SESSION_TTL = TimeUnit.MINUTES.toMillis(30);
    public static final long SESSION_UPDATE_TIMEOUT = TimeUnit.MINUTES.toMillis(2);

    // Processor's version for communicating with dispatcher
    private static final int PROCESSOR_COMM_VERSION = new ProcessorCommParamsYaml().version;

    private final Globals globals;
    private final VariableService variableService;
    private final FunctionDataService functionDataService;
    private final DispatcherCommandProcessor dispatcherCommandProcessor;
    private final ProcessorCache processorCache;
    private final ExecContextRepository execContextRepository;
    private final ProcessorRepository processorRepository;
    private final TaskRepository taskRepository;
    private final TaskPersistencer taskPersistencer;
    private final IdsRepository idsRepository;

    private static final ConcurrentHashMap<String, AtomicInteger> syncMap = new ConcurrentHashMap<>(100, 0.75f, 10);
    private static final ReentrantReadWriteLock.WriteLock writeLock = new ReentrantReadWriteLock().writeLock();

    @SuppressWarnings("Duplicates")
    private static <T> T getWithSync(final EnumsApi.BinaryType binaryType, final String code, Supplier<T> function) {
        final String key = "--" + binaryType + "--" + code;
        final AtomicInteger obj;
        try {
            writeLock.lock();
            obj = syncMap.computeIfAbsent(key, o -> new AtomicInteger());
        } finally {
            writeLock.unlock();
        }
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (obj) {
            obj.incrementAndGet();
            try {
                return function.get();
            } finally {
                try {
                    writeLock.lock();
                    if (obj.get() == 1) {
                        syncMap.remove(key);
                    }
                    obj.decrementAndGet();
                } finally {
                    writeLock.unlock();
                }
            }
        }
    }

    // return a requested resource to a processor
    public ResourceWithCleanerInfo deliverResource(final EnumsApi.BinaryType binaryType, final String resourceId, final String chunkSize, final int chunkNum) {
        return getWithSync(binaryType, resourceId,
                () -> getAbstractResourceResponseEntity(chunkSize, chunkNum, binaryType, resourceId));
    }

    public UploadResult uploadResource(MultipartFile file, Long resourceId) {
        String originFilename = file.getOriginalFilename();
        if (StringUtils.isBlank(originFilename)) {
            return new UploadResult(Enums.UploadResourceStatus.FILENAME_IS_BLANK, "#440.010 name of uploaded file is blank");
        }
        if (resourceId==null) {
            return new UploadResult(Enums.UploadResourceStatus.TASK_NOT_FOUND,"#440.020 resourceId is null" );
        }
        Variable variable = variableService.findById(resourceId).orElse(null);
        if (variable ==null) {
            return new UploadResult(Enums.UploadResourceStatus.TASK_NOT_FOUND,"#440.030 Variable for resourceId "+resourceId+" wasn't found" );
        }
        if (true) {
            throw new NotImplementedException("Need to re-write a logic of storing resources from processor");
        }
        final TaskParamsYaml taskParamYaml = TaskParamsYamlUtils.BASE_YAML_UTILS.to(variable.getParams());

        File tempDir=null;
        try {
            tempDir = DirUtils.createTempDir("upload-resource-");
            if (tempDir==null || tempDir.isFile()) {
                final String location = System.getProperty("java.io.tmpdir");
                return new UploadResult(Enums.UploadResourceStatus.GENERAL_ERROR, "#440.040 can't create temporary directory in " + location);
            }
            final File resFile = new File(tempDir, "resource.");
            log.debug("Start storing an uploaded resource data to disk, target file: {}", resFile.getPath());
            try(OutputStream os = new FileOutputStream(resFile)) {
                IOUtils.copy(file.getInputStream(), os, 64000);
            }
            try (InputStream is = new FileInputStream(resFile)) {
                variableService.update(is, resFile.length(), variable);
            }
        }
        catch (VariableSavingException th) {
            final String es = "#440.045 can't store the result, unrecoverable error with data. Error: " + th.toString();
            log.error(es, th);
            return new UploadResult(Enums.UploadResourceStatus.UNRECOVERABLE_ERROR, es);
        }
        catch (PessimisticLockingFailureException th) {
            final String es = "#440.050 can't store the result, need to try again. Error: " + th.toString();
            log.error(es, th);
            return new UploadResult(Enums.UploadResourceStatus.PROBLEM_WITH_LOCKING, es);
        }
        catch (Throwable th) {
            final String error = "#440.060 can't store the result, Error: " + th.toString();
            log.error(error, th);
            return new UploadResult(Enums.UploadResourceStatus.GENERAL_ERROR, error);
        }
        finally {
            DirUtils.deleteAsync(tempDir);
        }
        Enums.UploadResourceStatus status = taskPersistencer.setResultReceived(variable.getId(), true);
        return status== Enums.UploadResourceStatus.OK
                ? OK_UPLOAD_RESULT
                : new UploadResult(status, "#440.080 can't update resultReceived field for task #"+ variable.getId()+"");
    }

    private ResourceWithCleanerInfo getAbstractResourceResponseEntity(String chunkSize, int chunkNum, EnumsApi.BinaryType binaryType, String resourceId) {

        AssetFile assetFile;
        BiConsumer<String, File> dataSaver;
        switch (binaryType) {
            case function:
                assetFile = ResourceUtils.prepareFunctionFile(globals.dispatcherResourcesDir, resourceId, null);
                dataSaver = functionDataService::storeToFile;
                break;
            case data:
                assetFile = ResourceUtils.prepareDataFile(globals.dispatcherTempDir, resourceId, null);
                dataSaver = variableService::storeToFile;
                break;
            default:
                throw new IllegalStateException("#442.008 Unknown type of data: " + binaryType);
        }
        if (assetFile.isError) {
            String es = "#442.006 Resource with id " + resourceId + " is broken";
            log.error(es);
            throw new BinaryDataNotFoundException(es);
        }

        if (!assetFile.isContent) {
            try {
                dataSaver.accept(resourceId, assetFile.file);
            } catch (BinaryDataNotFoundException e) {
                log.error("#442.020 Error store data to temp file, data doesn't exist in db, id " + resourceId + ", file: " + assetFile.file.getPath());
                throw e;
            }
        }
        File f;
        boolean isLastChunk;
        ResourceWithCleanerInfo resource = new ResourceWithCleanerInfo();
        if (chunkSize == null || chunkSize.isBlank()) {
            f = assetFile.file;
            isLastChunk = true;
        } else {
            File tempDir = DirUtils.createTempDir("chunked-file-");
            f = new File(tempDir, "file-part.bin");
            resource.toClean.add(tempDir);

            final long size = Long.parseLong(chunkSize);
            final long offset = size * chunkNum;
            if (offset >= assetFile.file.length()) {
                MultiValueMap<String, String> headers = new HttpHeaders();
                headers.add(Consts.HEADER_MH_IS_LAST_CHUNK, "true");
                headers.add(Consts.HEADER_MH_CHUNK_SIZE, "0");
                resource.entity = new ResponseEntity<>(Consts.ZERO_BYTE_ARRAY_RESOURCE, headers, HttpStatus.OK);
                return resource;
            }
            final long realSize = assetFile.file.length() < offset + size ? assetFile.file.length() - offset : size;
            copyChunk(assetFile.file, f, offset, realSize);
            isLastChunk = (assetFile.file.length() == (offset + realSize));
        }
        final HttpHeaders headers = RestUtils.getHeader(f.length());
        headers.add(Consts.HEADER_MH_CHUNK_SIZE, Long.toString(f.length()));
        headers.add(Consts.HEADER_MH_IS_LAST_CHUNK, Boolean.toString(isLastChunk));
        resource.entity = new ResponseEntity<>(new FileSystemResource(f.toPath()), headers, HttpStatus.OK);
        return resource;
    }

    public static void copyChunk(File sourceFile, File destFile, long offset, long size) {

        try (final FileOutputStream fos = new FileOutputStream(destFile);
             RandomAccessFile raf = new RandomAccessFile(sourceFile,"r")){
            raf.seek(offset);
            long left = size;
            int realChunkSize = (int)(size > 64000 ? 64000 : size);
            byte[] bytes = new byte[realChunkSize];
            while (left>0) {
                int realSize = (int)(left>realChunkSize ? realChunkSize : left);
                int state;
                //noinspection CaughtExceptionImmediatelyRethrown
                try {
                    state = raf.read(bytes, 0, realSize);
                } catch (IndexOutOfBoundsException e) {
                    throw e;
                }
                if (state==-1) {
                    log.error("#442.030 Error in algo, read after EOF, file len: {}, offset: {}, size: {}", raf.length(), offset, size);
                    break;
                }
                fos.write(bytes, 0, realSize);
                left -= realSize;
            }
        }
        catch (Throwable th) {
            ExceptionUtils.wrapAndThrow(th);
        }
    }

    private DispatcherCommParamsYaml.ExecContextStatus getExecContextStatuses() {
        return new DispatcherCommParamsYaml.ExecContextStatus(
                execContextRepository.findAllExecStates()
                        .stream()
                        .map(o -> ServerService.toSimpleStatus((Long)o[0], (Integer)o[1]))
                        .collect(Collectors.toList()));
    }

    public String processRequest(String data, String remoteAddress) {
        ProcessorCommParamsYaml scpy = ProcessorCommParamsYamlUtils.BASE_YAML_UTILS.to(data);
        DispatcherCommParamsYaml lcpy = processRequestInternal(remoteAddress, scpy);
        //noinspection UnnecessaryLocalVariable
        String yaml = DispatcherCommParamsYamlUtils.BASE_YAML_UTILS.toString(lcpy);
        return yaml;
    }

    public DispatcherCommParamsYaml processRequestInternal(String remoteAddress, ProcessorCommParamsYaml scpy) {
        DispatcherCommParamsYaml lcpy = new DispatcherCommParamsYaml();
        try {
            if (scpy.processorCommContext ==null) {
                lcpy.assignedProcessorId = dispatcherCommandProcessor.getNewProcessorId(new ProcessorCommParamsYaml.RequestProcessorId());
                return lcpy;
            }
            checkProcessorId(scpy.processorCommContext.getProcessorId(), scpy.processorCommContext.getSessionId(), remoteAddress, lcpy);
            if (isProcessorContextNeedToBeChanged(lcpy)) {
                log.debug("isProcessorContextNeedToBeChanged is true, {}", lcpy);
                return lcpy;
            }

            lcpy.execContextStatus = getExecContextStatuses();

            log.debug("Start processing commands");
            dispatcherCommandProcessor.process(scpy, lcpy);
            setDispatcherCommContext(lcpy);
        } catch (Throwable th) {
            log.error("#442.040 Error while processing client's request, ProcessorCommParamsYaml:\n{}", scpy);
            log.error("#442.041 Error", th);
            lcpy.success = false;
            lcpy.msg = th.getMessage();
        }
        return lcpy;
    }

    private boolean isProcessorContextNeedToBeChanged(DispatcherCommParamsYaml lcpy) {
        return lcpy!=null && (lcpy.reAssignedProcessorId !=null || lcpy.assignedProcessorId !=null);
    }

    private void setDispatcherCommContext(DispatcherCommParamsYaml lcpy) {
        DispatcherCommParamsYaml.DispatcherCommContext lcc = new DispatcherCommParamsYaml.DispatcherCommContext();
        lcc.chunkSize = globals.chunkSize;
        lcc.processorCommVersion = PROCESSOR_COMM_VERSION;
        lcpy.dispatcherCommContext = lcc;
    }

    @SuppressWarnings("UnnecessaryReturnStatement")
    private void checkProcessorId(String processorId, String sessionId, String remoteAddress, DispatcherCommParamsYaml lcpy) {
        if (StringUtils.isBlank(processorId)) {
            log.warn("#442.045 StringUtils.isBlank(processorId), return RequestProcessorId()");
            lcpy.assignedProcessorId = dispatcherCommandProcessor.getNewProcessorId(new ProcessorCommParamsYaml.RequestProcessorId());
            return;
        }

        final Processor processor = processorRepository.findByIdForUpdate(Long.parseLong(processorId));
        if (processor == null) {
            log.warn("#442.046 processor == null, return ReAssignProcessorId() with new processorId and new sessionId");
            lcpy.reAssignedProcessorId = reassignProcessorId(remoteAddress, "Id was reassigned from " + processorId);
            return;
        }
        ProcessorStatusYaml ss;
        try {
            ss = ProcessorStatusYamlUtils.BASE_YAML_UTILS.to(processor.status);
        } catch (Throwable e) {
            log.error("#442.065 Error parsing current status of processor:\n{}", processor.status);
            log.error("#442.066 Error ", e);
            // skip any command from this processor
            return;
        }
        if (StringUtils.isBlank(sessionId)) {
            log.debug("#442.070 StringUtils.isBlank(sessionId), return ReAssignProcessorId() with new sessionId");
            // the same processor but with different and expired sessionId
            // so we can continue to use this processorId with new sessionId
            lcpy.reAssignedProcessorId = assignNewSessionId(processor, ss);
            return;
        }
        if (!ss.sessionId.equals(sessionId)) {
            if ((System.currentTimeMillis() - ss.sessionCreatedOn) > SESSION_TTL) {
                log.debug("#442.071 !ss.sessionId.equals(sessionId) && (System.currentTimeMillis() - ss.sessionCreatedOn) > SESSION_TTL, return ReAssignProcessorId() with new sessionId");
                // the same processor but with different and expired sessionId
                // so we can continue to use this processorId with new sessionId
                // we won't use processor's sessionIf to be sure that sessionId has valid format
                lcpy.reAssignedProcessorId = assignNewSessionId(processor, ss);
                return;
            } else {
                log.debug("#442.072 !ss.sessionId.equals(sessionId) && !((System.currentTimeMillis() - ss.sessionCreatedOn) > SESSION_TTL), return ReAssignProcessorId() with new processorId and new sessionId");
                // different processors with the same processorId
                // there is other active processor with valid sessionId
                lcpy.reAssignedProcessorId = reassignProcessorId(remoteAddress, "Id was reassigned from " + processorId);
                return;
            }
        }
        else {
            // see logs in method
            updateSession(processor, ss);
        }
    }

    /**
     * session is Ok, so we need to update session's timestamp periodically
     */
    @SuppressWarnings("UnnecessaryReturnStatement")
    private void updateSession(Processor processor, ProcessorStatusYaml ss) {
        final long millis = System.currentTimeMillis();
        final long diff = millis - ss.sessionCreatedOn;
        if (diff > SESSION_UPDATE_TIMEOUT) {
            log.debug("#442.074 (System.currentTimeMillis()-ss.sessionCreatedOn)>SESSION_UPDATE_TIMEOUT),\n" +
                    "'    processor.version: {}, millis: {}, ss.sessionCreatedOn: {}, diff: {}, SESSION_UPDATE_TIMEOUT: {},\n" +
                    "'    processor.status:\n{},\n" +
                    "'    return ReAssignProcessorId() with the same processorId and sessionId. only session's timestamp was updated.",
                    processor.version, millis, ss.sessionCreatedOn, diff, SESSION_UPDATE_TIMEOUT, processor.status);
            // the same processor, with the same sessionId
            // so we just need to refresh sessionId timestamp
            ss.sessionCreatedOn = millis;
            processor.updatedOn = millis;
            processor.status = ProcessorStatusYamlUtils.BASE_YAML_UTILS.toString(ss);
            try {
                processorCache.save(processor);
            } catch (ObjectOptimisticLockingFailureException e) {
                log.error("#442.080 Error saving processor. old : {}, new: {}", processorCache.findById(processor.id), processor);
                log.error("#442.085 Error");
                throw e;
            }
            Processor s = processorCache.findById(processor.id);
            log.debug("#442.086 old processor.version: {}, in cache processor.version: {}, processor.status:\n{},\n", processor.version, s.version, s.status);
            // the same processorId but new sessionId
            return;
        }
        else {
            // the same processorId, the same sessionId, session isn't expired
            return;
        }
    }

    private DispatcherCommParamsYaml.ReAssignProcessorId assignNewSessionId(Processor processor, ProcessorStatusYaml ss) {
        ss.sessionId = ProcessorTopLevelService.createNewSessionId();
        ss.sessionCreatedOn = System.currentTimeMillis();
        processor.status = ProcessorStatusYamlUtils.BASE_YAML_UTILS.toString(ss);
        processor.updatedOn = ss.sessionCreatedOn;
        processorCache.save(processor);
        // the same processorId but new sessionId
        return new DispatcherCommParamsYaml.ReAssignProcessorId(processor.getId(), ss.sessionId);
    }

    private DispatcherCommParamsYaml.ReAssignProcessorId reassignProcessorId(String remoteAddress, String description) {
        Processor s = new Processor();
        s.setIp(remoteAddress);
        s.setDescription(description);

        String sessionId = ProcessorTopLevelService.createNewSessionId();
        ProcessorStatusYaml ss = new ProcessorStatusYaml(new ArrayList<>(), null,
                new GitSourcingService.GitStatusInfo(Enums.GitStatus.unknown), "",
                sessionId, System.currentTimeMillis(),
                "[unknown]", "[unknown]", null, false, 1, EnumsApi.OS.unknown);

        s.status = ProcessorStatusYamlUtils.BASE_YAML_UTILS.toString(ss);
        s.updatedOn = ss.sessionCreatedOn;
        processorCache.save(s);
        return new DispatcherCommParamsYaml.ReAssignProcessorId(s.getId(), sessionId);
    }

    private static DispatcherCommParamsYaml.ExecContextStatus.SimpleStatus to(ExecContext execContext) {
        return new DispatcherCommParamsYaml.ExecContextStatus.SimpleStatus(execContext.getId(), EnumsApi.ExecContextState.toState(execContext.getState()));
    }

    private static DispatcherCommParamsYaml.ExecContextStatus.SimpleStatus toSimpleStatus(Long execContextId, Integer execSate) {
        return new DispatcherCommParamsYaml.ExecContextStatus.SimpleStatus(execContextId, EnumsApi.ExecContextState.toState(execSate));
    }

}
