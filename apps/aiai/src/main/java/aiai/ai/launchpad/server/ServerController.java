/*
 * AiAi, Copyright (C) 2017-2018  Serge Maslyukov
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
import aiai.ai.exceptions.BinaryDataNotFoundException;
import aiai.ai.launchpad.beans.Snippet;
import aiai.ai.launchpad.beans.Task;
import aiai.ai.launchpad.binary_data.BinaryDataService;
import aiai.ai.launchpad.repositories.SnippetRepository;
import aiai.ai.launchpad.repositories.TaskRepository;
import aiai.ai.launchpad.task.TaskPersistencer;
import aiai.ai.station.AssetFile;
import aiai.ai.utils.ResourceUtils;
import aiai.ai.yaml.task.TaskParamYaml;
import aiai.ai.yaml.task.TaskParamYamlUtils;
import aiai.apps.commons.utils.DirUtils;
import aiai.apps.commons.yaml.snippet.SnippetVersion;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.AbstractResource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
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
public class ServerController {

    private static final UploadResult OK_UPLOAD_RESULT = new UploadResult(Enums.UploadResourceStatus.OK, null);

    private final Globals globals;
    private final ServerService serverService;
    private final BinaryDataService binaryDataService;
    private final SnippetRepository snippetRepository;
    private final TaskRepository taskRepository;
    private final TaskPersistencer taskPersistencer;
    private final TaskParamYamlUtils taskParamYamlUtils;

    public ServerController(Globals globals, ServerService serverService, BinaryDataService binaryDataService, SnippetRepository snippetRepository, TaskRepository taskRepository, TaskPersistencer taskPersistencer, TaskParamYamlUtils taskParamYamlUtils) {
        this.globals = globals;
        this.serverService = serverService;
        this.binaryDataService = binaryDataService;
        this.snippetRepository = snippetRepository;
        this.taskRepository = taskRepository;
        this.taskPersistencer = taskPersistencer;
        this.taskParamYamlUtils = taskParamYamlUtils;
    }

    @PostMapping("/rest-anon/registry")
    public RegistryData getRegistryData() {
        return null;
        //
    }

    @PostMapping("/rest-anon/srv")
    public ExchangeData processRequestAnon(HttpServletResponse response, @RequestBody ExchangeData data, HttpServletRequest request) throws IOException {
        log.debug("processRequestAnon(), globals.isSecureRestUrl: {}, data: {}", globals.isSecureLaunchpadRestUrl, data);
        if (globals.isSecureLaunchpadRestUrl) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return null;
        }
        return serverService.processRequest(data, request.getRemoteAddr());
    }

    @PostMapping("/rest-auth/srv")
    public ExchangeData processRequestAuth(@RequestBody ExchangeData data, HttpServletRequest request) {
        log.debug("processRequestAuth(), globals.isSecureRestUrl: {}, data: {}", globals.isSecureLaunchpadRestUrl, data);
        return serverService.processRequest(data, request.getRemoteAddr());
    }

    @SuppressWarnings("unused")
    @GetMapping(value="/rest-anon/payload/resource/{type}/{random-part}", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public HttpEntity<AbstractResource> deliverResourceAnon(
            HttpServletResponse response,
            @PathVariable("type") String typeAsStr,
            @PathVariable("random-part") String randomPart,
            String stationId,
            Long taskId,
            String code) throws IOException {
        log.debug("deliverResourceAnon(), globals.isSecureRestUrl: {}, typeAsStr: {}, code: {}", globals.isSecureLaunchpadRestUrl, typeAsStr, code);
        if (globals.isSecureLaunchpadRestUrl) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return null;
        }
        return deliverResourceToStation(response, typeAsStr, code);
    }

    @SuppressWarnings("unused")
    @GetMapping(value="/rest-auth/payload/resource/{type}/{random-part}", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public HttpEntity<AbstractResource> deliverResourceAuth(
            HttpServletResponse response,
            @PathVariable("type") String typeAsStr,
            @PathVariable("random-part") String randomPart,
            String stationId,
            Long taskId,
            String code) throws IOException {
        log.debug("deliverResourceAuth(), globals.isSecureRestUrl: {}, typeAsStr: {}, code: {}", globals.isSecureLaunchpadRestUrl, typeAsStr, code);
        return deliverResourceToStation(response, typeAsStr, code);
    }

    @SuppressWarnings("unused")
    @PostMapping("/rest-anon/upload/{random-part}")
    public UploadResult uploadResourceAnon(
            HttpServletResponse response,
            MultipartFile file,
            String stationId,
            Long taskId,
            @PathVariable("random-part") String randomPart
    ) throws IOException {
        log.debug("uploadResourceAnon(), globals.isSecureRestUrl: {}, taskId: {}", globals.isSecureLaunchpadRestUrl, taskId);
        if (globals.isSecureLaunchpadRestUrl) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return null;
        }
        return uploadResource(file, taskId);
    }

    @SuppressWarnings("unused")
    @PostMapping("/rest-auth/upload/{random-part}")
    public UploadResult uploadResourceAuth(
            HttpServletResponse response,
            MultipartFile file,
            String stationId,
            Long taskId,
            @PathVariable("random-part") String randomPart
    ) throws IOException {
        log.debug("uploadResourceAuth(), globals.isSecureRestUrl: {}, taskId: {}", globals.isSecureLaunchpadRestUrl, taskId);
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
        Task task = taskRepository.findById(taskId).orElse(null);
        if (task==null) {
            return new UploadResult(Enums.UploadResourceStatus.TASK_NOT_FOUND,"#442.83 taskId is null" );
        }

        final TaskParamYaml taskParamYaml = taskParamYamlUtils.toTaskYaml(task.getParams());

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
                        is, resFile.length(), Enums.BinaryDataType.DATA,
                        taskParamYaml.outputResourceCode,
                        taskParamYaml.outputResourceCode,
                        false,
                        null,
                        task.flowInstanceId);
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

    private HttpEntity<AbstractResource> deliverResourceToStation(HttpServletResponse response, String typeAsStr, String code) throws IOException {
        Enums.BinaryDataType binaryDataType = Enums.BinaryDataType.valueOf(typeAsStr.toUpperCase());
        AssetFile assetFile;
        switch(binaryDataType) {
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

        if (assetFile==null) {
            log.error("#442.12 resource with code {} wasn't found", code);
            return returnEmptyAsGone(response);
        }
        try {
            binaryDataService.storeToFile(code, assetFile.file);
        } catch (BinaryDataNotFoundException e) {
            log.error("#442.16 Error store data to file, code " + code+", file: " + assetFile.file.getPath());
            return returnEmptyAsGone(response);
        }
        return new HttpEntity<>(new FileSystemResource(assetFile.file.toPath()), getHeader(assetFile.file.length()));
    }

    private HttpEntity<AbstractResource> returnEmptyAsGone(HttpServletResponse response) throws IOException {
        response.sendError(HttpServletResponse.SC_GONE);
        return new HttpEntity<>(new ByteArrayResource(new byte[0]), getHeader(0));
    }

    @SuppressWarnings("unused")
    @PostMapping("/rest-anon/payload/snippet-checksum/{random-part}")
    public String snippetChecksumAnon(
            HttpServletResponse response,
            String stationId,
            Long taskId,
            String code,
            @PathVariable("random-part") String randomPart
    ) throws IOException {
        log.debug("snippetChecksumAnon(), globals.isSecureRestUrl: {}, taskId: {}", globals.isSecureLaunchpadRestUrl, taskId);
        if (globals.isSecureLaunchpadRestUrl) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return null;
        }
        return getSnippetChecksum(response, code);
    }

    @SuppressWarnings("unused")
    @PostMapping("/rest-auth/payload/snippet-checksum/{random-part}")
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
        SnippetVersion snippetVersion = SnippetVersion.from(snippetCode);
        if (snippetVersion==null) {
            log.warn("#442.19 wrong format of snippet code {}", snippetCode);
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return null;
        }
        Snippet snippet = snippetRepository.findByNameAndSnippetVersion(snippetVersion.name, snippetVersion.version);
        if (snippet==null) {
            log.warn("#442.23 Snippet wasn't found for code {}", snippetCode);
            response.sendError(HttpServletResponse.SC_GONE);
            return null;
        }
        log.info("Send checksum for snippet {}");
        return snippet.getChecksum();
    }

    private static HttpHeaders getHeader(long length) {
        HttpHeaders header = new HttpHeaders();
        header.setContentLength(length);
        header.setCacheControl("max-age=0");
        header.setExpires(0);
        header.setPragma("no-cache");

        return header;
    }


    /**
     * This endpoint is only for testing security. Do not delete
     * @return String
     */
    @GetMapping("/rest-anon/test")
    public String getMessage_1() {
        return "Ok";
    }

    /**
     * This endpoint is only for testing security. Do not delete
     * @return String
     */
    @GetMapping("/rest-auth/test")
    public String getMessage_2() {
        return "Ok";
    }

}
