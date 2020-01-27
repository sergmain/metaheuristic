/*
 * Metaheuristic, Copyright (C) 2017-2019  Serge Maslyukov
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

package ai.metaheuristic.ai.launchpad.server;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.exceptions.BinaryDataNotFoundException;
import ai.metaheuristic.ai.exceptions.BinaryDataSaveException;
import ai.metaheuristic.ai.launchpad.LaunchpadCommandProcessor;
import ai.metaheuristic.ai.launchpad.beans.Station;
import ai.metaheuristic.ai.launchpad.beans.TaskImpl;
import ai.metaheuristic.ai.launchpad.binary_data.BinaryDataService;
import ai.metaheuristic.ai.launchpad.repositories.StationsRepository;
import ai.metaheuristic.ai.launchpad.repositories.TaskRepository;
import ai.metaheuristic.ai.launchpad.repositories.WorkbookRepository;
import ai.metaheuristic.ai.launchpad.snippet.SnippetBinaryDataService;
import ai.metaheuristic.ai.launchpad.station.StationCache;
import ai.metaheuristic.ai.launchpad.station.StationTopLevelService;
import ai.metaheuristic.ai.launchpad.task.TaskPersistencer;
import ai.metaheuristic.ai.resource.AssetFile;
import ai.metaheuristic.ai.resource.ResourceUtils;
import ai.metaheuristic.ai.resource.ResourceWithCleanerInfo;
import ai.metaheuristic.ai.station.sourcing.git.GitSourcingService;
import ai.metaheuristic.ai.utils.RestUtils;
import ai.metaheuristic.ai.yaml.communication.launchpad.LaunchpadCommParamsYaml;
import ai.metaheuristic.ai.yaml.communication.launchpad.LaunchpadCommParamsYamlUtils;
import ai.metaheuristic.ai.yaml.communication.station.StationCommParamsYaml;
import ai.metaheuristic.ai.yaml.communication.station.StationCommParamsYamlUtils;
import ai.metaheuristic.ai.yaml.station_status.StationStatusYaml;
import ai.metaheuristic.ai.yaml.station_status.StationStatusYamlUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.api.launchpad.Workbook;
import ai.metaheuristic.commons.utils.DirUtils;
import ai.metaheuristic.commons.yaml.task.TaskParamsYamlUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
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
@Profile("launchpad")
@Slf4j
@RequiredArgsConstructor
public class ServerService {

    private static final UploadResult OK_UPLOAD_RESULT = new UploadResult(Enums.UploadResourceStatus.OK, null);
    public static final long SESSION_TTL = TimeUnit.MINUTES.toMillis(30);
    public static final long SESSION_UPDATE_TIMEOUT = TimeUnit.MINUTES.toMillis(2);

    // Station's version for communicating with launchpad
    private static final int STATION_COMM_VERSION = new StationCommParamsYaml().version;

    private final Globals globals;
    private final BinaryDataService binaryDataService;
    private final SnippetBinaryDataService snippetBinaryDataService;
    private final LaunchpadCommandProcessor launchpadCommandProcessor;
    private final StationCache stationCache;
    private final WorkbookRepository workbookRepository;
    private final StationsRepository stationsRepository;
    private final TaskRepository taskRepository;
    private final TaskPersistencer taskPersistencer;

    private static final ConcurrentHashMap<String, AtomicInteger> syncMap = new ConcurrentHashMap<>(100, 0.75f, 10);
    private static final ReentrantReadWriteLock.WriteLock writeLock = new ReentrantReadWriteLock().writeLock();

    @SuppressWarnings("Duplicates")
    private static <T> T getWithSync(final EnumsApi.BinaryDataType binaryDataType, final String code, Supplier<T> function) {
        final String key = "--" + binaryDataType + "--" + code;
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

    // return a requested resource to a station
    public ResourceWithCleanerInfo deliverResource(final EnumsApi.BinaryDataType binaryDataType, final String code, final String chunkSize, final int chunkNum) {
        return getWithSync(binaryDataType, code,
                () -> getAbstractResourceResponseEntity(chunkSize, chunkNum, binaryDataType, code));
    }

    public UploadResult uploadResource(MultipartFile file, Long taskId) {
        String originFilename = file.getOriginalFilename();
        if (StringUtils.isBlank(originFilename)) {
            return new UploadResult(Enums.UploadResourceStatus.FILENAME_IS_BLANK, "#440.010 name of uploaded file is blank");
        }
        if (taskId==null) {
            return new UploadResult(Enums.UploadResourceStatus.TASK_NOT_FOUND,"#440.020 taskId is null" );
        }
        TaskImpl task = taskRepository.findById(taskId).orElse(null);
        if (task==null) {
            return new UploadResult(Enums.UploadResourceStatus.TASK_NOT_FOUND,"#440.030 taskId is null" );
        }

        final TaskParamsYaml taskParamYaml = TaskParamsYamlUtils.BASE_YAML_UTILS.to(task.getParams());

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
                binaryDataService.save(
                        is, resFile.length(), EnumsApi.BinaryDataType.DATA,
                        taskParamYaml.taskYaml.outputResourceCode,
                        null,
                        task.workbookId);
            }
        }
        catch (BinaryDataSaveException th) {
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
        Enums.UploadResourceStatus status = taskPersistencer.setResultReceived(task.getId(), true);
        return status== Enums.UploadResourceStatus.OK
                ? OK_UPLOAD_RESULT
                : new UploadResult(status, "#440.080 can't update resultReceived field for task #"+task.getId()+"");
    }

    private ResourceWithCleanerInfo getAbstractResourceResponseEntity(String chunkSize, int chunkNum, EnumsApi.BinaryDataType binaryDataType, String code) {

        AssetFile assetFile;
        BiConsumer<String, File> dataSaver;
        switch (binaryDataType) {
            case SNIPPET:
                assetFile = ResourceUtils.prepareSnippetFile(globals.launchpadResourcesDir, code, null);
                dataSaver = snippetBinaryDataService::storeToFile;
                break;
            case DATA:
            case TEST:
                assetFile = ResourceUtils.prepareDataFile(globals.launchpadTempDir, code, null);
                dataSaver = binaryDataService::storeToFile;
                break;
            case UNKNOWN:
            default:
                throw new IllegalStateException("#442.008 Unknown type of data: " + binaryDataType);
        }
        if (assetFile.isError) {
            String es = "#442.006 Resource with code " + code + " is broken";
            log.error(es);
            throw new BinaryDataNotFoundException(es);
        }

        if (!assetFile.isContent) {
            try {
                dataSaver.accept(code, assetFile.file);
            } catch (BinaryDataNotFoundException e) {
                log.error("#442.020 Error store data to temp file, data doesn't exist in db, code " + code + ", file: " + assetFile.file.getPath());
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

    private LaunchpadCommParamsYaml.WorkbookStatus getWorkbookStatuses() {
        return new LaunchpadCommParamsYaml.WorkbookStatus(
                workbookRepository.findAllExecStates()
                        .stream()
                        .map(o -> ServerService.toSimpleStatus((Long)o[0], (Integer)o[1]))
                        .collect(Collectors.toList()));
    }

    public String processRequest(String data, String remoteAddress) {
        StationCommParamsYaml scpy = StationCommParamsYamlUtils.BASE_YAML_UTILS.to(data);
        LaunchpadCommParamsYaml lcpy = processRequestInternal(remoteAddress, scpy);
        //noinspection UnnecessaryLocalVariable
        String yaml = LaunchpadCommParamsYamlUtils.BASE_YAML_UTILS.toString(lcpy);
        return yaml;
    }

    public LaunchpadCommParamsYaml processRequestInternal(String remoteAddress, StationCommParamsYaml scpy) {
        LaunchpadCommParamsYaml lcpy = new LaunchpadCommParamsYaml();
        try {
            if (scpy.stationCommContext==null) {
                lcpy.assignedStationId = launchpadCommandProcessor.getNewStationId(new StationCommParamsYaml.RequestStationId());
                return lcpy;
            }
            checkStationId(scpy.stationCommContext.getStationId(), scpy.stationCommContext.getSessionId(), remoteAddress, lcpy);
            if (isStationContextNeedToBeChanged(lcpy)) {
                log.debug("isStationContextNeedToBeChanged is true, {}", lcpy);
                return lcpy;
            }

            lcpy.workbookStatus = getWorkbookStatuses();

            log.debug("Start processing commands");
            launchpadCommandProcessor.process(scpy, lcpy);
            setLaunchpadCommContext(lcpy);
        } catch (Throwable th) {
            log.error("#442.040 Error while processing client's request, StationCommParamsYaml:\n{}", scpy);
            log.error("#442.041 Error", th);
            lcpy.success = false;
            lcpy.msg = th.getMessage();
        }
        return lcpy;
    }

    private boolean isStationContextNeedToBeChanged(LaunchpadCommParamsYaml lcpy) {
        return lcpy!=null && (lcpy.reAssignedStationId!=null || lcpy.assignedStationId!=null);
    }

    private void setLaunchpadCommContext(LaunchpadCommParamsYaml lcpy) {
        LaunchpadCommParamsYaml.LaunchpadCommContext lcc = new LaunchpadCommParamsYaml.LaunchpadCommContext();
        lcc.chunkSize = globals.chunkSize;
        lcc.stationCommVersion = STATION_COMM_VERSION;
        lcpy.launchpadCommContext = lcc;
    }

    @SuppressWarnings("UnnecessaryReturnStatement")
    private void checkStationId(String stationId, String sessionId, String remoteAddress, LaunchpadCommParamsYaml lcpy) {
        if (StringUtils.isBlank(stationId)) {
            log.warn("#442.045 StringUtils.isBlank(stationId), return RequestStationId()");
            lcpy.assignedStationId = launchpadCommandProcessor.getNewStationId(new StationCommParamsYaml.RequestStationId());
            return;
        }

        final Station station = stationsRepository.findByIdForUpdate(Long.parseLong(stationId));
        if (station == null) {
            log.warn("#442.046 station == null, return ReAssignStationId() with new stationId and new sessionId");
            lcpy.reAssignedStationId = reassignStationId(remoteAddress, "Id was reassigned from " + stationId);
            return;
        }
        StationStatusYaml ss;
        try {
            ss = StationStatusYamlUtils.BASE_YAML_UTILS.to(station.status);
        } catch (Throwable e) {
            log.error("#442.065 Error parsing current status of station:\n{}", station.status);
            log.error("#442.066 Error ", e);
            // skip any command from this station
            return;
        }
        if (StringUtils.isBlank(sessionId)) {
            log.debug("#442.070 StringUtils.isBlank(sessionId), return ReAssignStationId() with new sessionId");
            // the same station but with different and expired sessionId
            // so we can continue to use this stationId with new sessionId
            lcpy.reAssignedStationId = assignNewSessionId(station, ss);
            return;
        }
        if (!ss.sessionId.equals(sessionId)) {
            if ((System.currentTimeMillis() - ss.sessionCreatedOn) > SESSION_TTL) {
                log.debug("#442.071 !ss.sessionId.equals(sessionId) && (System.currentTimeMillis() - ss.sessionCreatedOn) > SESSION_TTL, return ReAssignStationId() with new sessionId");
                // the same station but with different and expired sessionId
                // so we can continue to use this stationId with new sessionId
                // we won't use station's sessionIf to be sure that sessionId has valid format
                lcpy.reAssignedStationId = assignNewSessionId(station, ss);
                return;
            } else {
                log.debug("#442.072 !ss.sessionId.equals(sessionId) && !((System.currentTimeMillis() - ss.sessionCreatedOn) > SESSION_TTL), return ReAssignStationId() with new stationId and new sessionId");
                // different stations with the same stationId
                // there is other active station with valid sessionId
                lcpy.reAssignedStationId = reassignStationId(remoteAddress, "Id was reassigned from " + stationId);
                return;
            }
        }
        else {
            // see logs in method
            updateSession(station, ss);
        }
    }

    /**
     * session is Ok, so we need to update session's timestamp periodically
     */
    @SuppressWarnings("UnnecessaryReturnStatement")
    private void updateSession(Station station, StationStatusYaml ss) {
        final long millis = System.currentTimeMillis();
        final long diff = millis - ss.sessionCreatedOn;
        if (diff > SESSION_UPDATE_TIMEOUT) {
            log.debug("#442.074 (System.currentTimeMillis()-ss.sessionCreatedOn)>SESSION_UPDATE_TIMEOUT),\n" +
                    "'    station.version: {}, millis: {}, ss.sessionCreatedOn: {}, diff: {}, SESSION_UPDATE_TIMEOUT: {},\n" +
                    "'    station.status:\n{},\n" +
                    "'    return ReAssignStationId() with the same stationId and sessionId. only session's timestamp was updated.",
                    station.version, millis, ss.sessionCreatedOn, diff, SESSION_UPDATE_TIMEOUT, station.status);
            // the same station, with the same sessionId
            // so we just need to refresh sessionId timestamp
            ss.sessionCreatedOn = millis;
            station.updatedOn = millis;
            station.status = StationStatusYamlUtils.BASE_YAML_UTILS.toString(ss);
            try {
                stationCache.save(station);
            } catch (ObjectOptimisticLockingFailureException e) {
                log.error("#442.080 Error saving station. old : {}, new: {}", stationCache.findById(station.id), station);
                log.error("#442.085 Error");
                throw e;
            }
            Station s = stationCache.findById(station.id);
            log.debug("#442.086 old station.version: {}, in cache station.version: {}, station.status:\n{},\n", station.version, s.version, s.status);
            // the same stationId but new sessionId
            return;
        }
        else {
            // the same stationId, the same sessionId, session isn't expired
            return;
        }
    }

    private LaunchpadCommParamsYaml.ReAssignStationId assignNewSessionId(Station station, StationStatusYaml ss) {
        ss.sessionId = StationTopLevelService.createNewSessionId();
        ss.sessionCreatedOn = System.currentTimeMillis();
        station.status = StationStatusYamlUtils.BASE_YAML_UTILS.toString(ss);
        station.updatedOn = ss.sessionCreatedOn;
        stationCache.save(station);
        // the same stationId but new sessionId
        return new LaunchpadCommParamsYaml.ReAssignStationId(station.getId(), ss.sessionId);
    }

    private LaunchpadCommParamsYaml.ReAssignStationId reassignStationId(String remoteAddress, String description) {
        Station s = new Station();
        s.setIp(remoteAddress);
        s.setDescription(description);

        String sessionId = StationTopLevelService.createNewSessionId();
        StationStatusYaml ss = new StationStatusYaml(new ArrayList<>(), null,
                new GitSourcingService.GitStatusInfo(Enums.GitStatus.unknown), "",
                sessionId, System.currentTimeMillis(),
                "[unknown]", "[unknown]", null, false, 1, EnumsApi.OS.unknown);

        s.status = StationStatusYamlUtils.BASE_YAML_UTILS.toString(ss);
        s.updatedOn = ss.sessionCreatedOn;
        stationCache.save(s);
        return new LaunchpadCommParamsYaml.ReAssignStationId(s.getId(), sessionId);
    }

    private static LaunchpadCommParamsYaml.WorkbookStatus.SimpleStatus to(Workbook workbook) {
        return new LaunchpadCommParamsYaml.WorkbookStatus.SimpleStatus(workbook.getId(), EnumsApi.WorkbookExecState.toState(workbook.getExecState()));
    }

    private static LaunchpadCommParamsYaml.WorkbookStatus.SimpleStatus toSimpleStatus(Long workbookId, Integer execSate) {
        return new LaunchpadCommParamsYaml.WorkbookStatus.SimpleStatus(workbookId, EnumsApi.WorkbookExecState.toState(execSate));
    }

}
