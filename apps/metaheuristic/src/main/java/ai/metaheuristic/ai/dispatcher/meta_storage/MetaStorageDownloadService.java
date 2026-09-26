/*
 * Metaheuristic, Copyright (C) 2017-2026, Innovation platforms, LLC
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

package ai.metaheuristic.ai.dispatcher.meta_storage;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.utils.HttpUtils;
import ai.metaheuristic.ai.utils.RestUtils;
import ai.metaheuristic.ai.utils.cleaner.CleanerInfo;
import ai.metaheuristic.commons.utils.DirUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * One whole meta table, offered as a zip download - a directory named after the table, one file per
 * record. The layout and the naming rules live in {@link MetaStorageExportUtils}; this class supplies
 * the store, the temp directory and the HTTP shape.
 *
 * <p>Synchronous: the key list is read, then bodies in chunks of
 * {@link MetaStorageService#MAX_KEYS_PER_QUERY}, each chunk written out before the next is read. A
 * table of any size therefore holds at most one chunk of bodies in memory - reading every body at once
 * would put a whole MEDIUMTEXT table on the heap for the length of the request.
 *
 * <p>Non-transactional, per SPRING-TX-RULES.md: every read is a repository call with its own read-only
 * transaction, and holding one open across the filesystem work would pin a connection for as long as
 * the disk takes.
 *
 * <p>Error code prefix: {@code 01.950.} (unique to this class).
 *
 * @author Serge
 */
@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class MetaStorageDownloadService {

    private final MetaStorageService metaStorageService;
    private final MetaStorageSyntheticService metaStorageSyntheticService;

    /**
     * ❗ The temp dir is put into {@code toClean} BEFORE anything is written into it, so a failure half
     * way still leaves the caller something to delete. The caller hands {@code toClean} to
     * {@code CleanerInterceptor}, which removes it once the response has been written.
     *
     * @param production true reads MH_META_STORAGE, false MH_META_STORAGE_SYNTHETIC. The caller has
     *                   already resolved {@code companyId} to what the principal may read.
     */
    public CleanerInfo download(Long companyId, String metaTable, boolean production) {
        final CleanerInfo resource = new CleanerInfo();
        try {
            final Path tempDir = DirUtils.createMhTempPath("meta-table-download-");
            if (tempDir==null) {
                resource.addErrorMessage("01.950.020 Can't create a temporary directory");
                return resource;
            }
            resource.toClean.add(tempDir);

            final List<String> recKeys = production
                    ? metaStorageService.listKeys(companyId, metaTable)
                    : metaStorageSyntheticService.listKeys(companyId, metaTable);

            final MetaStorageExportUtils.Export export = MetaStorageExportUtils.exportToZip(
                    tempDir, metaTable, recKeys,
                    keys -> production
                            ? metaStorageService.select(companyId, metaTable, keys)
                            : metaStorageSyntheticService.select(companyId, metaTable, keys),
                    MetaStorageService.MAX_KEYS_PER_QUERY);

            log.info("01.950.040 meta table '{}' of company #{} (production={}) exported, {} record(s)",
                    metaTable, companyId, production, export.records());

            final HttpHeaders httpHeaders = new HttpHeaders();
            httpHeaders.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            HttpUtils.setContentDisposition(httpHeaders, export.zipFileName());
            resource.entity = new ResponseEntity<>(new FileSystemResource(export.zip()),
                    RestUtils.getHeader(httpHeaders, Files.size(export.zip())), HttpStatus.OK);
            return resource;
        }
        catch (Throwable th) {
            log.error("01.950.060 Error while exporting meta table '{}' of company #{} (production={})",
                    metaTable, companyId, production, th);
            resource.addErrorMessage("01.950.080 Error while exporting meta table '" + metaTable + "': " + th.getMessage());
            resource.entity = new ResponseEntity<>(Consts.ZERO_BYTE_ARRAY_RESOURCE, HttpStatus.GONE);
            return resource;
        }
    }
}
