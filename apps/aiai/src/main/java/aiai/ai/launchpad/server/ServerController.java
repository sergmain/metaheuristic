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
import aiai.ai.launchpad.repositories.TaskRepository;
import aiai.ai.launchpad.snippet.SnippetCache;
import aiai.ai.launchpad.snippet.SnippetService;
import aiai.ai.launchpad.task.TaskPersistencer;
import aiai.ai.station.AssetFile;
import aiai.ai.station.StationResourceUtils;
import aiai.ai.utils.checksum.ChecksumWithSignatureService;
import aiai.ai.yaml.task.TaskParamYaml;
import aiai.ai.yaml.task.TaskParamYamlUtils;
import aiai.apps.commons.utils.DirUtils;
import aiai.apps.commons.yaml.snippet.SnippetVersion;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.AbstractResource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
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

    private static final UploadResult OK_UPLOAD_RESULT = new UploadResult(true, null);

    private final Globals globals;
    private final ServerService serverService;
    private final BinaryDataService binaryDataService;
    private final SnippetCache snippetCache;
    private final SnippetService snippetService;
    private final ChecksumWithSignatureService checksumWithSignatureService;
    private final TaskRepository taskRepository;
    private final TaskPersistencer taskPersistencer;
    private final TaskParamYamlUtils taskParamYamlUtils;

    public ServerController(Globals globals, ServerService serverService, BinaryDataService binaryDataService, SnippetCache snippetCache, SnippetService snippetService, ChecksumWithSignatureService checksumWithSignatureService, TaskRepository taskRepository, TaskPersistencer taskPersistencer, TaskParamYamlUtils taskParamYamlUtils) {
        this.globals = globals;
        this.serverService = serverService;
        this.binaryDataService = binaryDataService;
        this.snippetCache = snippetCache;
        this.snippetService = snippetService;
        this.checksumWithSignatureService = checksumWithSignatureService;
        this.taskRepository = taskRepository;
        this.taskPersistencer = taskPersistencer;
        this.taskParamYamlUtils = taskParamYamlUtils;
    }

    @PostMapping("/rest-anon/srv")
    public ExchangeData processRequestAnon(HttpServletResponse response, @RequestBody ExchangeData data, HttpServletRequest request) throws IOException {
        log.debug("processRequestAnon(), globals.isSecureRestUrl: {}, data: {}", globals.isSecureRestUrl, data);
        if (globals.isSecureRestUrl) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return null;
        }
        return serverService.processRequest(data, request.getRemoteAddr());
    }

    @PostMapping("/rest-auth/srv")
    public ExchangeData processRequestAuth(@RequestBody ExchangeData data, HttpServletRequest request) {
        log.debug("processRequestAnon(), globals.isSecureRestUrl: {}, data: {}", globals.isSecureRestUrl, data);
        return serverService.processRequest(data, request.getRemoteAddr());
    }

    @GetMapping("/rest-anon/payload/resource/{type}/{code}")
    public HttpEntity<AbstractResource> deliverResourceAnon(HttpServletResponse response, @PathVariable("type") String typeAsStr, @PathVariable("code") String code) throws IOException {
        log.debug("deliverResourceAnon(), globals.isSecureRestUrl: {}, typeAsStr: {}, code: {}", globals.isSecureRestUrl, typeAsStr, code);
        if (globals.isSecureRestUrl) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return null;
        }
        return deliverResource(response, typeAsStr, code);
    }

    @GetMapping("/rest-auth/payload/resource/{type}/{code}")
    public HttpEntity<AbstractResource> deliverResourceAuth(HttpServletResponse response, @PathVariable("type") String typeAsStr, @PathVariable("code") String code) throws IOException {
        log.debug("deliverResourceAuth(), globals.isSecureRestUrl: {}, typeAsStr: {}, code: {}", globals.isSecureRestUrl, typeAsStr, code);
        return deliverResource(response, typeAsStr, code);
    }

    @GetMapping("/rest-anon/upload/{taskId}")
    public UploadResult uploadResourceAnon(
            MultipartFile file, HttpServletResponse response,
            @PathVariable("taskId") Long taskId) throws IOException {
        log.debug("uploadResourceAnon(), globals.isSecureRestUrl: {}, taskId: {}", globals.isSecureRestUrl, taskId);
        if (globals.isSecureRestUrl) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return null;
        }
        return uploadResource(file, taskId);
    }

    @PostMapping("/rest-auth/upload/{taskId}")
    public UploadResult uploadResourceAuth(
            MultipartFile file,
            @PathVariable("taskId") Long taskId) {
        log.debug("uploadResourceAuth(), globals.isSecureRestUrl: {}, taskId: {}", globals.isSecureRestUrl, taskId);
        return uploadResource(file, taskId);
    }

    private UploadResult uploadResource(MultipartFile file, Long taskId) {
        String originFilename = file.getOriginalFilename();
        if (originFilename == null) {
            return new UploadResult(false, "#442.01 name of uploaded file is null");
        }
        if (taskId==null) {
            return new UploadResult(false,"#442.87 taskId is null" );
        }
        Task task = taskRepository.findById(taskId).orElse(null);
        if (task==null) {
            return new UploadResult(false,"#442.83 taskId is null" );
        }

        final TaskParamYaml taskParamYaml = taskParamYamlUtils.toTaskYaml(task.getParams());

        try {
            File tempDir = DirUtils.createTempDir("upload-resource-");
            if (tempDir==null || tempDir.isFile()) {
                final String location = System.getProperty("java.io.tmpdir");
                return new UploadResult(false, "#442.04 can't create temporary directory in " + location);
            }
            final File resFile = new File(tempDir, "resource.");
            log.debug("Start storing an uploaded resource data to disk");
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
            log.error("Error", th);
            return new UploadResult(false, "#442.05 can't upload result, Error: " + th.toString());
        }
        Task result = taskPersistencer.setResultReceived(task.getId(), true);
        return result!=null ? OK_UPLOAD_RESULT : new UploadResult(false, "#442.08 can't update resultReceived field for task #"+task.getId()+". See log for info.");
    }

    private HttpEntity<AbstractResource> deliverResource(HttpServletResponse response, String typeAsStr, String code) throws IOException {
        Enums.BinaryDataType binaryDataType = Enums.BinaryDataType.valueOf(typeAsStr.toUpperCase());
        AssetFile assetFile;
        switch(binaryDataType) {
            case SNIPPET:
                assetFile = StationResourceUtils.prepareSnippetFile(globals.launchpadResourcesDir, code, null);
                break;
            case DATA:
            case TEST:
                assetFile = StationResourceUtils.prepareDataFile(globals.launchpadResourcesDir, code, null);
                break;
            case UNKNOWN:
            default:
                throw new IllegalStateException("Unknown type of data: " + binaryDataType);
        }

        if (assetFile==null) {
            return returnEmptyAsGone(response);
        }
        try {
            binaryDataService.storeToFile(code, assetFile.file);
        } catch (BinaryDataNotFoundException e) {
            return returnEmptyAsGone(response);
        }
        return new HttpEntity<>(new FileSystemResource(assetFile.file.toPath()), getHeader(assetFile.file.length()));
    }

    private HttpEntity<AbstractResource> returnEmptyAsGone(HttpServletResponse response) throws IOException {
        response.sendError(HttpServletResponse.SC_GONE);
        return new HttpEntity<>(new ByteArrayResource(new byte[0]), getHeader(0));
    }

    private HttpEntity<AbstractResource> returnEmptyAsConflict(HttpServletResponse response) throws IOException {
        response.sendError(HttpServletResponse.SC_CONFLICT);
        return new HttpEntity<>(new ByteArrayResource(new byte[0]), getHeader(0));
    }

    @GetMapping("/rest-auth/payload/snippet-checksum/{name}")
    public HttpEntity<String> snippetChecksum(HttpServletResponse response, @PathVariable("name") String snippetCode) throws IOException {

        SnippetVersion snippetVersion = SnippetVersion.from(snippetCode);
        Snippet snippet = snippetCache.findByNameAndSnippetVersion(snippetVersion.name, snippetVersion.version);
        if (snippet==null) {
            log.warn("Snippet wasn't found for name {}", snippetCode);
            return returnEmptyStringWithStatus(response, HttpServletResponse.SC_GONE);
        }
/*
        File snippetFile;
        AssetFile assetFile = StationResourceUtils.prepareSnippetFile(globals.launchpadResourcesDir, Enums.BinaryDataType.SNIPPET, snippetCode);
        if (assetFile==null) {
            log.warn("Snippet wasn't found for name {}", snippetCode);
            return returnEmptyStringWithStatus(response, HttpServletResponse.SC_GONE);
        }
        try {
            binaryDataService.storeToFile(snippetCode, assetFile.file);
        } catch (BinaryDataNotFoundException e) {
            log.error("Error store data to file", e);
            return returnEmptyStringWithStatus(response, HttpServletResponse.SC_GONE);
        }

        Checksum checksum = Checksum.fromJson(snippet.getChecksum());
        try (InputStream is = new FileInputStream(assetFile.file)) {
            CheckSumAndSignatureStatus status = checksumWithSignatureService.verifyChecksumAndSignature(
                    checksum, snippetCode, is, false
            );
            if (!status.isOk) {
                return returnEmptyStringWithStatus(response, HttpServletResponse.SC_CONFLICT);
            }
        }*/
        final int length = snippet.getChecksum().length();
        log.info("Send checksum for snippet {}, length: {}", snippet.getSnippetCode(), length);

        return new HttpEntity<>(snippet.getChecksum(), getHeader(length) );
    }

    private HttpEntity<String> returnEmptyStringWithStatus(HttpServletResponse response, int status) throws IOException {
        response.sendError(status);
        return new HttpEntity<>("", getHeader(0));
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
