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
import ai.metaheuristic.ai.launchpad.beans.Snippet;
import ai.metaheuristic.ai.launchpad.beans.TaskImpl;
import ai.metaheuristic.ai.launchpad.binary_data.BinaryDataService;
import ai.metaheuristic.ai.launchpad.repositories.SnippetRepository;
import ai.metaheuristic.ai.launchpad.repositories.TaskRepository;
import ai.metaheuristic.ai.launchpad.task.TaskPersistencer;
import ai.metaheuristic.ai.resource.ResourceWithCleanerInfo;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.SnippetApiData;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.commons.utils.DirUtils;
import ai.metaheuristic.commons.yaml.snippet.SnippetConfigUtils;
import ai.metaheuristic.commons.yaml.task.TaskParamsYamlUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.AbstractResource;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
@PreAuthorize("hasAnyRole('ADMIN', 'SERVER_REST_ACCESS')")
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
    public String processRequestAuth(@SuppressWarnings("unused") @PathVariable("random-part") String randomPart,@RequestBody String data) {
        log.debug("processRequestAuth(), globals.isSecurityEnabled: {}, data: {}", globals.isSecurityEnabled, data);
        return "{}";
    }

    @PostMapping("/srv-v2/{random-part}")
    public String processRequestAuth(
            HttpServletRequest request,
            @SuppressWarnings("unused") @PathVariable("random-part") String randomPart,
            @RequestBody String data
            ) {
        log.debug("processRequestAuth(), globals.isSecurityEnabled: {}, data: {}", globals.isSecurityEnabled, data);
        return serverService.processRequest(data, request.getRemoteAddr());
    }

    @GetMapping(value="/payload/resource/{type}/{random-part}", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<AbstractResource> deliverResourceAuth(
            HttpServletRequest request,
            @PathVariable("type") String typeAsStr,
            @SuppressWarnings("unused") @PathVariable("random-part") String randomPart,
            @SuppressWarnings("unused") String stationId,
            @SuppressWarnings("unused") Long taskId,
            String code, String chunkSize, Integer chunkNum) {
        String normalCode = new File(code).getName();
        log.debug("deliverResourceAuth(), globals.isSecurityEnabled: {}, typeAsStr: {}, code: {}, chunkSize: {}, chunkNum: {}",
                globals.isSecurityEnabled, typeAsStr, normalCode, chunkSize, chunkNum);
        if (chunkSize==null || chunkSize.isBlank() || chunkNum==null) {
            return new ResponseEntity<>(Consts.ZERO_BYTE_ARRAY_RESOURCE, HttpStatus.BAD_REQUEST);
        }

        final ResponseEntity<AbstractResource> entity;
        try {
            ResourceWithCleanerInfo resource = serverService.deliverResource(typeAsStr, normalCode, chunkSize, chunkNum);
            entity = resource.entity;
            request.setAttribute(Consts.RESOURCES_TO_CLEAN, resource.toClean);
        } catch (BinaryDataNotFoundException e) {
            return new ResponseEntity<>(Consts.ZERO_BYTE_ARRAY_RESOURCE, HttpStatus.GONE);
        }
        return entity;
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
                        taskParamYaml.taskYaml.outputResourceCode,
                        false,
                        null,
                        task.workbookId, EnumsApi.BinaryDataRefType.workbook);
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
            log.warn("#440.100 Snippet wasn't found for code {}", snippetCode);
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
