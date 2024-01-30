/*
 * Metaheuristic, Copyright (C) 2017-2024, Innovation platforms, LLC
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

package ai.metaheuristic.ai.dispatcher.event;

import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.dispatcher.DispatcherContext;
import ai.metaheuristic.ai.dispatcher.beans.DispatcherEvent;
import ai.metaheuristic.ai.dispatcher.event.events.DispatcherApplicationEvent;
import ai.metaheuristic.ai.dispatcher.repositories.CompanyRepository;
import ai.metaheuristic.ai.dispatcher.repositories.DispatcherEventRepository;
import ai.metaheuristic.ai.utils.RestUtils;
import ai.metaheuristic.ai.utils.cleaner.CleanerInfo;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.CompanyApiData;
import ai.metaheuristic.api.data.event.DispatcherEventYaml;
import ai.metaheuristic.commons.utils.DirUtils;
import ai.metaheuristic.commons.utils.ZipUtils;
import ai.metaheuristic.commons.yaml.YamlUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static ai.metaheuristic.commons.CommonConsts.EVENT_DATE_TIME_FORMATTER;

/**
 * @author Serge
 * Date: 10/14/2019
 * Time: 5:34 PM
 */
@SuppressWarnings("DuplicatedCode")
@Slf4j
@Service
@Profile("dispatcher")
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class DispatcherEventService {

    private static final int PAGE_SIZE = 1000;

    private final Globals globals;
    private final DispatcherEventRepository dispatcherEventRepository;
    private final CompanyRepository companyRepository;
    private final ApplicationEventPublisher applicationEventPublisher;

    public void publishBatchEvent(
            EnumsApi.DispatcherEventType event, @Nullable Long companyUniqueId, @Nullable String filename,
            @Nullable Long size, @Nullable Long batchId, @Nullable Long execContextId, @Nullable DispatcherContext dispatcherContext) {
        if (!globals.eventEnabled) {
            return;
        }
        if (event==EnumsApi.DispatcherEventType.BATCH_CREATED && (batchId==null || dispatcherContext ==null)) {
            throw new IllegalStateException("Error (event==Enums.DispatcherEventType.BATCH_CREATED && (batchId==null || dispatcherContext==null))");
        }
        DispatcherEventYaml.BatchEventData batchEventData = new DispatcherEventYaml.BatchEventData();
        batchEventData.filename = filename;
        batchEventData.size = size;
        batchEventData.batchId = batchId;
        batchEventData.execContextId = execContextId;
        String contextId = null;
        if (dispatcherContext !=null) {
            batchEventData.companyId = dispatcherContext.getCompanyId();
            batchEventData.username = dispatcherContext.getUsername();
            contextId = dispatcherContext.contextId;
        }
        applicationEventPublisher.publishEvent(new DispatcherApplicationEvent(event, companyUniqueId, contextId, batchEventData));
    }

    public void publishTaskEvent(
            EnumsApi.DispatcherEventType event, @Nullable Long coreId, Long taskId, Long execContextId,
            @Nullable EnumsApi.FunctionExecContext context, @Nullable String funcCode) {
        if (!globals.eventEnabled) {
            return;
        }
        DispatcherEventYaml.TaskEventData taskEventData = new DispatcherEventYaml.TaskEventData();
        taskEventData.coreId = coreId;
        taskEventData.taskId = taskId;
        taskEventData.execContextId = execContextId;
        taskEventData.context = context;
        taskEventData.funcCode = funcCode;
        applicationEventPublisher.publishEvent(new DispatcherApplicationEvent(event, taskEventData));
    }

    @Async
    @EventListener
    public void handleAsync(DispatcherApplicationEvent event) {
        try {
            if (!globals.eventEnabled) {
                return;
            }
            DispatcherEvent le = new DispatcherEvent();
            le.companyId = event.companyUniqueId;
            le.period = getPeriod( LocalDateTime.parse( event.dispatcherEventYaml.createdOn, EVENT_DATE_TIME_FORMATTER) );
            le.event = event.dispatcherEventYaml.event.toString();
            le.updateParams(event.dispatcherEventYaml);
            dispatcherEventRepository.save(le);
        } catch (Throwable th) {
            log.error("Error, need to investigate ", th);
        }
    }

    public static int getPeriod(LocalDateTime createdOn) {
        return createdOn.getYear() * 100 + createdOn.getMonthValue();
    }

    public static int getPeriod(LocalDate createdOn) {
        return createdOn.getYear() * 100 + createdOn.getMonthValue();
    }

    public void publishEventBatchFinished(Long batchId) {
        publishBatchEvent(EnumsApi.DispatcherEventType.BATCH_PROCESSING_FINISHED, null, null, null, batchId, null, null );
    }

    public static class ListOfEvents {
        public List<String> events;
    }

    public CleanerInfo getEventsForPeriod(List<Integer> periods) throws IOException {
        CleanerInfo resource = new CleanerInfo();

        Path tempDir = DirUtils.createMhTempPath("events-");
        if (tempDir==null) {
            throw new RuntimeException("(tempDir==null)");
        }
        resource.toClean.add(tempDir);
        Path filesDir = tempDir.resolve("files");
        Files.createDirectory(filesDir);

        List<Long> ids = dispatcherEventRepository.findIdByPeriod(periods);
        if (!ids.isEmpty()) {
            Yaml yaml = YamlUtils.init(ListOfEvents.class);

            for (int i = 0; i < ids.size() / PAGE_SIZE + 1; i++) {
                int fromIndex = i * PAGE_SIZE;
                List<Long> subList = ids.subList(fromIndex, Math.min(ids.size(), fromIndex + PAGE_SIZE));
                List<DispatcherEvent> events = dispatcherEventRepository.findByIds(subList);
                ListOfEvents listOfEvents = new ListOfEvents();
                listOfEvents.events = new ArrayList<>();
                for (DispatcherEvent event : events) {
                    listOfEvents.events.add(event.getParams());
                }
                Path f = filesDir.resolve("event-file-" + i + ".yaml");
                Files.writeString(f, yaml.dumpAsMap(listOfEvents));
            }
        }
        CompanyApiData.CompanyList companyList = new CompanyApiData.CompanyList();
        companyRepository.findAll()
                .forEach(c-> companyList.companies.add(new CompanyApiData.CompanyShortData(c.uniqueId, c.name)));

        Path companyYamlFile = filesDir.resolve("companies.yaml");
        Yaml companyYaml = YamlUtils.init(ListOfEvents.class);
        Files.writeString(companyYamlFile, companyYaml.dumpAsMap(companyList));


        Path zipFile = tempDir.resolve("events.zip");
        ZipUtils.createZip(filesDir, zipFile);

        final HttpHeaders headers = RestUtils.getHeader(null, Files.size(zipFile));
//        headers.add(Consts.HEADER_MH_CHUNK_SIZE, Long.toString(f.length()));
//        headers.add(Consts.HEADER_MH_IS_LAST_CHUNK, Boolean.toString(isLastChunk));

        log.warn("#456.020 size of zip archive with billing events is " + Files.size(zipFile));
        resource.entity = new ResponseEntity<>(new FileSystemResource(zipFile), headers, HttpStatus.OK);
        return resource;
    }
}
