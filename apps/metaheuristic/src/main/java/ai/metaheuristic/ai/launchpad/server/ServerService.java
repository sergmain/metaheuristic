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
import ai.metaheuristic.ai.launchpad.LaunchpadCommandProcessor;
import ai.metaheuristic.ai.launchpad.beans.Station;
import ai.metaheuristic.ai.launchpad.binary_data.BinaryDataService;
import ai.metaheuristic.ai.launchpad.repositories.StationsRepository;
import ai.metaheuristic.ai.launchpad.repositories.WorkbookRepository;
import ai.metaheuristic.ai.launchpad.station.StationCache;
import ai.metaheuristic.ai.launchpad.station.StationTopLevelService;
import ai.metaheuristic.ai.resource.AssetFile;
import ai.metaheuristic.ai.resource.ResourceUtils;
import ai.metaheuristic.ai.station.sourcing.git.GitSourcingService;
import ai.metaheuristic.ai.utils.RestUtils;
import ai.metaheuristic.ai.yaml.communication.launchpad.LaunchpadCommParamsYaml;
import ai.metaheuristic.ai.yaml.communication.launchpad.LaunchpadCommParamsYamlUtils;
import ai.metaheuristic.ai.yaml.communication.station.StationCommParamsYaml;
import ai.metaheuristic.ai.yaml.communication.station.StationCommParamsYamlUtils;
import ai.metaheuristic.ai.yaml.station_status.StationStatus;
import ai.metaheuristic.ai.yaml.station_status.StationStatusUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.launchpad.Workbook;
import ai.metaheuristic.commons.utils.DirUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.AbstractResource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.util.MultiValueMap;

import java.io.File;
import java.io.FileOutputStream;
import java.io.RandomAccessFile;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Service
@Profile("launchpad")
@Slf4j
@RequiredArgsConstructor
public class ServerService {

    public static final long SESSION_TTL = TimeUnit.MINUTES.toMillis(30);
    public static final long SESSION_UPDATE_TIMEOUT = TimeUnit.MINUTES.toMillis(2);

    private final Globals globals;
    private final BinaryDataService binaryDataService;
    private final LaunchpadCommandProcessor launchpadCommandProcessor;
    private final StationCache stationCache;
    private final WorkbookRepository workbookRepository;
    private final StationsRepository stationsRepository;

    // return a requested resource to a station
    public ResponseEntity<AbstractResource> deliverResource(String typeAsStr, String code, String chunkSize, int chunkNum) {
        EnumsApi.BinaryDataType binaryDataType = EnumsApi.BinaryDataType.valueOf(typeAsStr.toUpperCase());
        return deliverResource(binaryDataType, code, chunkSize, chunkNum);
    }

    // return a requested resource to a station
    public ResponseEntity<AbstractResource> deliverResource(EnumsApi.BinaryDataType binaryDataType, String code, String chunkSize, int chunkNum) {
        return deliverResource(binaryDataType, code, null, chunkSize, chunkNum);
    }

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
    private ResponseEntity<AbstractResource> deliverResource(final EnumsApi.BinaryDataType binaryDataType, final String code, final HttpHeaders httpHeaders, final String chunkSize, final int chunkNum) {
        return getWithSync(binaryDataType, code,
                () -> getAbstractResourceResponseEntity(httpHeaders, chunkSize, chunkNum, binaryDataType, code));
    }

    private ResponseEntity<AbstractResource> getAbstractResourceResponseEntity(HttpHeaders httpHeaders, String chunkSize, int chunkNum, EnumsApi.BinaryDataType binaryDataType, String code) {
        AssetFile assetFile;
        switch (binaryDataType) {
            case SNIPPET:
                assetFile = ResourceUtils.prepareSnippetFile(globals.launchpadResourcesDir, code, null);
                break;
            case DATA:
            case TEST:
                assetFile = ResourceUtils.prepareDataFile(globals.launchpadResourcesDir, code, null);
                break;
            case UNKNOWN:
            default:
                throw new IllegalStateException("Unknown type of data: " + binaryDataType);
        }

        if (assetFile == null) {
            String es = "#442.010 resource with code " + code + " wasn't found";
            log.error(es);
            throw new BinaryDataNotFoundException(es);
        }
        try {
            binaryDataService.storeToFile(code, assetFile.file);
        } catch (BinaryDataNotFoundException e) {
            log.error("#442.020 Error store data to temp file, data doesn't exist in db, code " + code + ", file: " + assetFile.file.getPath());
            throw e;
        }
        File f;
        boolean isLastChunk;
        if (chunkSize == null || chunkSize.isBlank()) {
            f = assetFile.file;
            isLastChunk = true;
        } else {
            f = new File(DirUtils.createTempDir("chunked-file-"), "file-part.bin");
            final long size = Long.parseLong(chunkSize);
            final long offset = size * chunkNum;
            if (offset >= assetFile.file.length()) {
                MultiValueMap<String, String> headers = new HttpHeaders();
                headers.add(Consts.HEADER_MH_IS_LAST_CHUNK, "true");
                headers.add(Consts.HEADER_MH_CHUNK_SIZE, "0");
                return new ResponseEntity<>(new ByteArrayResource(new byte[0]), headers, HttpStatus.OK);
            }
            final long realSize = assetFile.file.length() < offset + size ? assetFile.file.length() - offset : size;
            copyChunk(assetFile.file, f, offset, realSize);
            isLastChunk = (assetFile.file.length() == (offset + realSize));
        }
        final HttpHeaders headers = RestUtils.getHeader(httpHeaders, f.length());
        headers.add(Consts.HEADER_MH_CHUNK_SIZE, Long.toString(f.length()));
        headers.add(Consts.HEADER_MH_IS_LAST_CHUNK, Boolean.toString(isLastChunk));
        return new ResponseEntity<>(new FileSystemResource(f.toPath()), headers, HttpStatus.OK);
    }

    public static void copyChunk(File sourceFile, File destFile, long offset, long size) {

        try (final FileOutputStream fos = new FileOutputStream(destFile)){
            RandomAccessFile raf = new RandomAccessFile(sourceFile,"r");
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
        LaunchpadCommParamsYaml lcpy = new LaunchpadCommParamsYaml();
        StationCommParamsYaml scpy = StationCommParamsYamlUtils.BASE_YAML_UTILS.to(data);
        processRequestInternal(remoteAddress, scpy, lcpy);
        //noinspection UnnecessaryLocalVariable
        String yaml = LaunchpadCommParamsYamlUtils.BASE_YAML_UTILS.toString(lcpy);
        return yaml;
    }

    public void processRequestInternal(String remoteAddress, StationCommParamsYaml scpy, LaunchpadCommParamsYaml lcpy) {
        try {
            if (scpy.stationCommContext==null) {
                lcpy.assignedStationId = launchpadCommandProcessor.getNewStationId(new StationCommParamsYaml.RequestStationId());
                return;
            }
            checkStationId(scpy.stationCommContext.getStationId(), scpy.stationCommContext.getSessionId(), remoteAddress, lcpy);
            if (isStationContextNeedToBeChanged(lcpy)) {
                log.debug("isStationContextNeedToBeChanged is true, {}", lcpy);
                return;
            }

            lcpy.workbookStatus = getWorkbookStatuses();

            log.debug("Start processing commands");
            launchpadCommandProcessor.process(scpy, lcpy);
            addLaunchpadInfo(lcpy);
        } catch (Throwable th) {
            log.error("#442.040 Error while processing client's request, LaunchpadCommParamsYaml:\n{}", lcpy);
            log.error("#442.041 Error", th);
            lcpy.success = false;
            lcpy.msg = th.getMessage();
        }
    }

    private boolean isStationContextNeedToBeChanged(LaunchpadCommParamsYaml lcpy) {
        return lcpy!=null && (lcpy.reAssignedStationId!=null || lcpy.assignedStationId!=null);
    }

    private void addLaunchpadInfo(LaunchpadCommParamsYaml lcpy) {
        LaunchpadCommParamsYaml.LaunchpadCommContext lcc = new LaunchpadCommParamsYaml.LaunchpadCommContext();
        lcc.chunkSize = globals.chunkSize;
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
        StationStatus ss;
        try {
            ss = StationStatusUtils.to(station.status);
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
    private void updateSession(Station station, StationStatus ss) {
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
            station.status = StationStatusUtils.toString(ss);
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

    private LaunchpadCommParamsYaml.ReAssignStationId assignNewSessionId(Station station, StationStatus ss) {
        ss.sessionId = StationTopLevelService.createNewSessionId();
        ss.sessionCreatedOn = System.currentTimeMillis();
        station.status = StationStatusUtils.toString(ss);
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
        StationStatus ss = new StationStatus(null,
                new GitSourcingService.GitStatusInfo(Enums.GitStatus.unknown), "",
                sessionId, System.currentTimeMillis(),
                "[unknown]", "[unknown]", null, false, 1);

        s.status = StationStatusUtils.toString(ss);
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
