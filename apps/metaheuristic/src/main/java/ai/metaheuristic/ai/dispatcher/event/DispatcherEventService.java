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

package ai.metaheuristic.ai.dispatcher.event;

import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.dispatcher.DispatcherContext;
import ai.metaheuristic.ai.dispatcher.beans.DispatcherEvent;
import ai.metaheuristic.ai.dispatcher.repositories.CompanyRepository;
import ai.metaheuristic.ai.dispatcher.repositories.DispatcherEventRepository;
import ai.metaheuristic.ai.utils.cleaner.CleanerInfo;
import ai.metaheuristic.ai.utils.RestUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.CompanyApiData;
import ai.metaheuristic.api.data.event.DispatcherEventYaml;
import ai.metaheuristic.commons.utils.DirUtils;
import ai.metaheuristic.commons.utils.ZipUtils;
import ai.metaheuristic.commons.yaml.YamlUtils;
import ai.metaheuristic.commons.yaml.event.DispatcherEventYamlUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
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

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
@RequiredArgsConstructor
@Profile("dispatcher")
public class DispatcherEventService {

    private static final int PAGE_SIZE = 1000;

    private final Globals globals;
    private final DispatcherEventRepository dispatcherEventRepository;
    private final CompanyRepository companyRepository;
    private final ApplicationEventPublisher applicationEventPublisher;

    public void publishExecContextLockingEvent(EnumsApi.DispatcherEventType event, Long processorId, Long taskId, Long execContextId) {
        if (!globals.eventEnabled) {
            return;
        }
        DispatcherEventYaml.TaskEventData taskEventData = new DispatcherEventYaml.TaskEventData();
        taskEventData.processorId = processorId;
        taskEventData.taskId = taskId;
        taskEventData.execContextId = execContextId;
        applicationEventPublisher.publishEvent(new DispatcherApplicationEvent(event, taskEventData));
    }

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

    public void publishTaskEvent(EnumsApi.DispatcherEventType event, @Nullable Long processorId, Long taskId, Long execContextId) {
        if (!globals.eventEnabled) {
            return;
        }
        DispatcherEventYaml.TaskEventData taskEventData = new DispatcherEventYaml.TaskEventData();
        taskEventData.processorId = processorId;
        taskEventData.taskId = taskId;
        taskEventData.execContextId = execContextId;
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
            le.params = DispatcherEventYamlUtils.BASE_YAML_UTILS.toString(event.dispatcherEventYaml);
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

        File tempDir = DirUtils.createMhTempDir("events-");
        resource.toClean.add(tempDir);
        File filesDir = new File(tempDir, "files");

        List<Long> ids = dispatcherEventRepository.findIdByPeriod(periods);
        if (!ids.isEmpty()) {
            Yaml yaml = YamlUtils.init(ListOfEvents.class);

            for (int i = 0; i < ids.size() / PAGE_SIZE + 1; i++) {
                File f = new File(filesDir, "event-file-" + i + ".yaml");
                int fromIndex = i * PAGE_SIZE;
                List<Long> subList = ids.subList(fromIndex, Math.min(ids.size(), fromIndex + PAGE_SIZE));
                List<DispatcherEvent> events = dispatcherEventRepository.findByIds(subList);
                ListOfEvents listOfEvents = new ListOfEvents();
                listOfEvents.events = new ArrayList<>();
                for (DispatcherEvent event : events) {
                    listOfEvents.events.add(event.params);
                }
                FileUtils.write(f, yaml.dumpAsMap(listOfEvents), StandardCharsets.UTF_8);
            }
        }
        CompanyApiData.CompanyList companyList = new CompanyApiData.CompanyList();
        companyRepository.findAll()
                .forEach(c-> companyList.companies.add(new CompanyApiData.CompanyShortData(c.uniqueId, c.name)));

        File companyYamlFile = new File(filesDir, "companies.yaml");
        Yaml companyYaml = YamlUtils.init(ListOfEvents.class);
        FileUtils.write(companyYamlFile, companyYaml.dumpAsMap(companyList), StandardCharsets.UTF_8);


        File zipFile = new File(tempDir, "events.zip");
        ZipUtils.createZip(filesDir, zipFile);

        final HttpHeaders headers = RestUtils.getHeader(null, zipFile.length());
//        headers.add(Consts.HEADER_MH_CHUNK_SIZE, Long.toString(f.length()));
//        headers.add(Consts.HEADER_MH_IS_LAST_CHUNK, Boolean.toString(isLastChunk));

        log.warn("#456.020 size of zip archive with billing events is " + zipFile.length());
        resource.entity = new ResponseEntity<>(new FileSystemResource(zipFile.toPath()), headers, HttpStatus.OK);
        return resource;
    }
}
