/*
 * Metaheuristic, Copyright (C) 2017-2025, Innovation platforms, LLC
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

package ai.metaheuristic.ai.dispatcher.storage;

import ai.metaheuristic.ai.dispatcher.repositories.CacheVariableRepository;
import ai.metaheuristic.ai.exceptions.VariableDataNotFoundException;
import ai.metaheuristic.api.EnumsApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Blob;
import java.sql.SQLException;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * @author Sergio Lissner
 * Date: 8/18/2023
 * Time: 2:17 PM
 */
@Slf4j
@Service
@Profile({"dispatcher & !disk-storage"})
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class CacheVariableDatabaseStorageService {

    private final CacheVariableRepository cacheVariableRepository;

    public void accessCacheVariableData(Long cacheVariableId, Consumer<InputStream> processBlobDataFunc) throws SQLException, IOException {
        Blob blob = cacheVariableRepository.getDataAsStreamById(Objects.requireNonNull(cacheVariableId));
        if (blob==null) {
            String es = "174.420 Variable #"+ cacheVariableId +" wasn't found";
            log.warn(es);
            throw new VariableDataNotFoundException(cacheVariableId, EnumsApi.VariableContext.local, es);
        }
        try (InputStream is = blob.getBinaryStream(); BufferedInputStream bis = new BufferedInputStream(is)) {
            processBlobDataFunc.accept(bis);
        }
    }
}
