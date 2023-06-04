/*
 * Metaheuristic, Copyright (C) 2017-2023, Innovation platforms, LLC
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

package ai.metaheuristic.ai.dispatcher.variable_global;

import ai.metaheuristic.ai.dispatcher.beans.GlobalVariable;
import ai.metaheuristic.ai.dispatcher.repositories.GlobalVariableRepository;
import ai.metaheuristic.ai.yaml.data_storage.DataStorageParamsUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data_storage.DataStorageParams;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.engine.spi.SessionImplementor;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.sql.Blob;
import java.sql.Timestamp;

/**
 * @author Sergio Lissner
 * Date: 5/26/2023
 * Time: 2:59 AM
 */
@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor
public class GlobalVariableEntityManagerTxService {

    private final EntityManager em;
    private final GlobalVariableRepository globalVariableRepository;

    @Transactional
    public GlobalVariable save(InputStream is, long size, String variable, @Nullable String filename) {
        GlobalVariable data = new GlobalVariable();
        data.setName(variable);
        data.setFilename(filename);
        data.setParams(DataStorageParamsUtils.toString(new DataStorageParams(EnumsApi.DataSourcing.dispatcher, variable)));
        data.setUploadTs(new Timestamp(System.currentTimeMillis()));

        Blob blob = em.unwrap(SessionImplementor.class).getLobCreator().createBlob(is, size);
        data.setData(blob);

        globalVariableRepository.save(data);

        return data;
    }

    @Transactional
    public void update(InputStream is, long size, GlobalVariable data) {
        data.setUploadTs(new Timestamp(System.currentTimeMillis()));

        Blob blob = em.unwrap(SessionImplementor.class).getLobCreator().createBlob(is, size);
        data.setData(blob);

        globalVariableRepository.save(data);
    }


}
