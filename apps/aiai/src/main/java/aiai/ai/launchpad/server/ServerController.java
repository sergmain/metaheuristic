/*
 * AiAi, Copyright (C) 2017-2019  Serge Maslyukov
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package aiai.ai.launchpad.server;

import aiai.ai.Enums;
import aiai.ai.Globals;
import aiai.ai.comm.ExchangeData;
import aiai.ai.launchpad.beans.Snippet;
import aiai.ai.launchpad.beans.TaskImpl;
import aiai.ai.launchpad.binary_data.BinaryDataService;
import aiai.ai.launchpad.repositories.SnippetRepository;
import aiai.ai.launchpad.repositories.TaskRepository;
import aiai.ai.launchpad.task.TaskPersistencer;
import aiai.ai.yaml.task.TaskParamYamlUtils;
import aiai.api.v1.EnumsApi;
import aiai.api.v1.data.SnippetApiData;
import aiai.api.v1.data.TaskApiData;
import aiai.apps.commons.utils.DirUtils;
import aiai.apps.commons.yaml.snippet.SnippetConfigUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.AbstractResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;

/**
 * User: Serg
 * Date: 13.07.2017
 * Time: 19:19
 */
@RestController
@Slf4j
@Profile("launchpad")
@RequestMapping("/rest/v1")
public class ServerController {

    private static final UploadResult OK_UPLOAD_RESULT = new UploadResult(Enums.UploadResourceStatus.OK, null);

    private final Globals globals;
    private final ServerService serverService;
    private final BinaryDataService binaryDataService;
    private final SnippetRepository snippetRepository;
    private final TaskRepository taskRepository;
    private final TaskPersistencer taskPersistencer;

    public ServerController(Globals globals, ServerService serverService, BinaryDataService binaryDataService, SnippetRepository snippetRepository, TaskRepository taskRepository, TaskPersistencer taskPersistencer) {
        this.globals = globals;
        this.serverService = serverService;
        this.binaryDataService = binaryDataService;
        this.snippetRepository = snippetRepository;
        this.taskRepository = taskRepository;
        this.taskPersistencer = taskPersistencer;
    }

    @PostMapping("/registry")
    public RegistryData getRegistryData() {
        return null;
        //
    }

    @PostMapping("/srv/{random-part}")
    public ExchangeData processRequestAuth(
            HttpServletRequest request,
            @SuppressWarnings("unused") @PathVariable("random-part") String randomPart,
            @RequestBody ExchangeData data
            ) {
        log.debug("processRequestAuth(), globals.isSecurityEnabled: {}, data: {}", globals.isSecurityEnabled, data);
        return serverService.processRequest(data, request.getRemoteAddr());
    }

    @GetMapping(value="/payload/resource/{type}/{random-part}", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public HttpEntity<AbstractResource> deliverResourceAuth(
            @PathVariable("type") String typeAsStr,
            @SuppressWarnings("unused") @PathVariable("random-part") String randomPart,
            @SuppressWarnings("unused") String stationId,
            @SuppressWarnings("unused") Long taskId,
            String code) {
        log.debug("deliverResourceAuth(), globals.isSecurityEnabled: {}, typeAsStr: {}, code: {}", globals.isSecurityEnabled, typeAsStr, code);
        return serverService.deliverResource(typeAsStr, code);
    }

    @PostMapping("/upload/{random-part}")
    public UploadResult uploadResourceAuth(
            MultipartFile file,
            @SuppressWarnings("unused") String stationId,
            Long taskId,
            @SuppressWarnings("unused") @PathVariable("random-part") String randomPart
    ) {
        log.debug("uploadResourceAuth(), globals.isSecurityEnabled: {}, taskId: {}", globals.isSecurityEnabled, taskId);
        return uploadResource(file, taskId);
    }

    private UploadResult uploadResource(MultipartFile file, Long taskId) {
        String originFilename = file.getOriginalFilename();
        if (StringUtils.isBlank(originFilename)) {
            return new UploadResult(Enums.UploadResourceStatus.FILENAME_IS_BLANK, "#442.01 name of uploaded file is blank");
        }
        if (taskId==null) {
            return new UploadResult(Enums.UploadResourceStatus.TASK_NOT_FOUND,"#442.87 taskId is null" );
        }
        TaskImpl task = taskRepository.findById(taskId).orElse(null);
        if (task==null) {
            return new UploadResult(Enums.UploadResourceStatus.TASK_NOT_FOUND,"#442.83 taskId is null" );
        }

        final TaskApiData.TaskParamYaml taskParamYaml = TaskParamYamlUtils.toTaskYaml(task.getParams());

        try {
            File tempDir = DirUtils.createTempDir("upload-resource-");
            if (tempDir==null || tempDir.isFile()) {
                final String location = System.getProperty("java.io.tmpdir");
                return new UploadResult(Enums.UploadResourceStatus.GENERAL_ERROR, "#442.04 can't create temporary directory in " + location);
            }
            final File resFile = new File(tempDir, "resource.");
            log.debug("Start storing an uploaded resource data to disk, target file: {}", resFile.getPath());
            try(OutputStream os = new FileOutputStream(resFile)) {
                IOUtils.copy(file.getInputStream(), os, 64000);
            }
            try (InputStream is = new FileInputStream(resFile)) {
                binaryDataService.save(
                        is, resFile.length(), EnumsApi.BinaryDataType.DATA,
                        taskParamYaml.outputResourceCode,
                        taskParamYaml.outputResourceCode,
                        false,
                        null,
                        task.workbookId);
            }
        }
        catch (Throwable th) {
            log.error("#442.01 Error", th);
            return new UploadResult(Enums.UploadResourceStatus.GENERAL_ERROR, "#442.05 can't upload result, Error: " + th.toString());
        }
        Enums.UploadResourceStatus status = taskPersistencer.setResultReceived(task.getId(), true);
        return status== Enums.UploadResourceStatus.OK
                ? OK_UPLOAD_RESULT
                : new UploadResult(status, "#442.08 can't update resultReceived field for task #"+task.getId()+"");
    }

    @SuppressWarnings("unused")
    @PostMapping("/payload/snippet-checksum/{random-part}")
    public String snippetChecksumAuth(
            HttpServletResponse response,
            String stationId,
            String taskId,
            String code,
            @PathVariable("random-part") String randomPart
    ) throws IOException {
        return getSnippetChecksum(response, code);
    }

    private String getSnippetChecksum(HttpServletResponse response, String snippetCode) throws IOException {
        Snippet snippet = snippetRepository.findByCode(snippetCode);
        if (snippet==null) {
            log.warn("#442.23 Snippet wasn't found for code {}", snippetCode);
            response.sendError(HttpServletResponse.SC_GONE);
            return null;
        }
        SnippetApiData.SnippetConfig sc = SnippetConfigUtils.to(snippet.params);
        log.info("Send checksum {} for snippet {}", sc.checksum, sc.getCode());
        return sc.checksum;
    }

    /**
     * This endpoint is only for testing security. Do not delete
     * @return String
     */
    @GetMapping("/test")
    public String getMessage_2() {
        return "Ok";
    }

}
