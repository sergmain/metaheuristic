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

package ai.metaheuristic.ai.mh.dispatcher..event;

import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.mh.dispatcher..DispatcherContext;
import ai.metaheuristic.ai.mh.dispatcher..beans.DispatcherEvent;
import ai.metaheuristic.ai.mh.dispatcher..repositories.CompanyRepository;
import ai.metaheuristic.ai.mh.dispatcher..repositories.DispatcherEventRepository;
import ai.metaheuristic.ai.resource.ResourceWithCleanerInfo;
import ai.metaheuristic.ai.utils.RestUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.CompanyData;
import ai.metaheuristic.api.data.event.DispatcherEventYaml;
import ai.metaheuristic.commons.utils.DirUtils;
import ai.metaheuristic.commons.utils.ZipUtils;
import ai.metaheuristic.commons.yaml.YamlUtils;
import ai.metaheuristic.commons.yaml.event.LaunchpadEventYamlUtils;
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
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static ai.metaheuristic.commons.CommonConsts.EVENT_DATE_TIME_FORMATTER;

/**
 * @author Serge
 * Date: 10/14/2019
 * Time: 5:34 PM
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Profile("mh.dispatcher.")
public class DispatcherEventService {

    private final Globals globals;
    private final DispatcherEventRepository mh.dispatcher.EventRepository;
    private final CompanyRepository companyRepository;
    private final ApplicationEventPublisher applicationEventPublisher;

    public void publishExecContextLockingEvent(EnumsApi.DispatcherEventType event, Long stationId, Long taskId, Long execContextId) {
        if (!globals.isEventEnabled) {
            return;
        }
        DispatcherEventYaml.TaskEventData taskEventData = new DispatcherEventYaml.TaskEventData();
        taskEventData.stationId = stationId;
        taskEventData.taskId = taskId;
        taskEventData.execContextId = execContextId;
        applicationEventPublisher.publishEvent(new DispatcherApplicationEvent(event, taskEventData));
    }

    public void publishBatchEvent(
            EnumsApi.DispatcherEventType event, Long companyUniqueId, String filename,
            Long size, Long batchId, Long execContextId, DispatcherContext mh.dispatcher.Context) {
        if (!globals.isEventEnabled) {
            return;
        }
        if (event== EnumsApi.DispatcherEventType.BATCH_CREATED && (batchId==null || mh.dispatcher.Context ==null)) {
            throw new IllegalStateException("Error (event==Enums.DispatcherEventType.BATCH_CREATED && (batchId==null || mh.dispatcher.Context==null))");
        }
        DispatcherEventYaml.BatchEventData batchEventData = new DispatcherEventYaml.BatchEventData();
        batchEventData.filename = filename;
        batchEventData.size = size;
        batchEventData.batchId = batchId;
        batchEventData.execContextId = execContextId;
        String contextId = null;
        if (mh.dispatcher.Context !=null) {
            batchEventData.companyId = mh.dispatcher.Context.getCompanyId();
            batchEventData.username = mh.dispatcher.Context.getUsername();
            contextId = mh.dispatcher.Context.contextId;
        }
        applicationEventPublisher.publishEvent(new DispatcherApplicationEvent(event, companyUniqueId, contextId, batchEventData));
    }

    public void publishTaskEvent(EnumsApi.DispatcherEventType event, Long stationId, Long taskId, Long execContextId) {
        if (!globals.isEventEnabled) {
            return;
        }
        DispatcherEventYaml.TaskEventData taskEventData = new DispatcherEventYaml.TaskEventData();
        taskEventData.stationId = stationId;
        taskEventData.taskId = taskId;
        taskEventData.execContextId = execContextId;
        applicationEventPublisher.publishEvent(new DispatcherApplicationEvent(event, taskEventData));
    }

    @Async
    @EventListener
    public void handleAsync(DispatcherApplicationEvent event) {
        if (!globals.isEventEnabled) {
            return;
        }
        DispatcherEvent le = new DispatcherEvent();
        le.companyId = event.companyUniqueId;
        le.period = getPeriod( LocalDateTime.parse( event.mh.dispatcher.EventYaml.createdOn, EVENT_DATE_TIME_FORMATTER) );
        le.event = event.mh.dispatcher.EventYaml.event.toString();
        le.params = LaunchpadEventYamlUtils.BASE_YAML_UTILS.toString(event.mh.dispatcher.EventYaml);
        mh.dispatcher.EventRepository.save(le);
    }

    private static int getPeriod(LocalDateTime createdOn) {
        return createdOn.getYear() * 100 + createdOn.getMonthValue();
    }

    private static final int PAGE_SIZE = 1000;

    public static class ListOfEvents {
        public List<String> events;
    }

    public ResourceWithCleanerInfo getEventsForPeriod(List<Integer> periods) throws IOException {
        ResourceWithCleanerInfo resource = new ResourceWithCleanerInfo();

        File tempDir = DirUtils.createTempDir("events-");
        resource.toClean.add(tempDir);
        File filesDir = new File(tempDir, "files");

        List<Long> ids = mh.dispatcher.EventRepository.findIdByPeriod(periods);
        if (!ids.isEmpty()) {
            Yaml yaml = YamlUtils.init(ListOfEvents.class);

            for (int i = 0; i < ids.size() / PAGE_SIZE + 1; i++) {
                File f = new File(filesDir, "event-file-" + i + ".yaml");
                int fromIndex = i * PAGE_SIZE;
                List<Long> subList = ids.subList(fromIndex, Math.min(ids.size(), fromIndex + PAGE_SIZE));
                List<DispatcherEvent> events = mh.dispatcher.EventRepository.findByIds(subList);
                ListOfEvents listOfEvents = new ListOfEvents();
                listOfEvents.events = new ArrayList<>();
                for (DispatcherEvent event : events) {
                    listOfEvents.events.add(event.params);
                }
                FileUtils.write(f, yaml.dumpAsMap(listOfEvents), StandardCharsets.UTF_8);
            }
        }
        CompanyData.CompanyList companyList = new CompanyData.CompanyList();
        companyRepository.findAll()
                .forEach(c-> companyList.companies.add(new CompanyData.CompanyShortData(c.uniqueId, c.name)));

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
