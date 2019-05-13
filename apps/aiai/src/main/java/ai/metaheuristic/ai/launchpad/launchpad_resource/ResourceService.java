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

package ai.metaheuristic.ai.launchpad.launchpad_resource;

import ai.metaheuristic.ai.exceptions.StoreNewFileException;
import ai.metaheuristic.ai.launchpad.binary_data.BinaryDataService;
import ai.metaheuristic.api.v1.EnumsApi;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

@Service
@Slf4j
@Profile("launchpad")
public class ResourceService {

    private final BinaryDataService binaryDataService;

    public ResourceService(BinaryDataService binaryDataService) {
        this.binaryDataService = binaryDataService;
    }

    public void storeInitialResource(File tempFile, String code, String poolCode, String filename) {
        try {
            try (InputStream is = new FileInputStream(tempFile)) {
                binaryDataService.save(
                        is, tempFile.length(), EnumsApi.BinaryDataType.DATA, code, poolCode, true, filename, null);
            }
        } catch (IOException e) {
            log.error("Error", e);
            throw new StoreNewFileException("Error while storing", e, tempFile.getPath(), filename);
        }
    }

}
