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

package ai.metaheuristic.ai.dispatcher.function;

import ai.metaheuristic.ai.dispatcher.beans.FunctionData;
import ai.metaheuristic.ai.dispatcher.repositories.FunctionDataRepository;
import ai.metaheuristic.ai.dispatcher.storage.DispatcherBlobStorage;
import ai.metaheuristic.ai.exceptions.FunctionDataErrorException;
import ai.metaheuristic.ai.utils.TxUtils;
import ai.metaheuristic.ai.yaml.data_storage.DataStorageParamsUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data_storage.DataStorageParams;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.engine.spi.SessionImplementor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.sql.Timestamp;

/**
 * @author Sergio Lissner
 * Date: 5/26/2023
 * Time: 2:53 AM
 */
@Service
@Transactional
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class FunctionDataWithEntityManagerService {
    private final EntityManager em;
    private final FunctionDataRepository functionDataRepository;
    private final DispatcherBlobStorage dispatcherBlobStorage;

    public void update(InputStream is, long size, FunctionData data) {
        dispatcherBlobStorage.storeFunctionData(data.id, is, size);
    }

    public FunctionData save(InputStream is, long size, String functionCode) {
        TxUtils.checkTxExists();
        dispatcherBlobStorage.storeFunctionData(data.id, is, size);

        try {
            FunctionData data = functionDataRepository.findByCodeForUpdate(functionCode);
            if (data == null) {
                data = new FunctionData();
                data.setFunctionCode(functionCode);
                data.setParams(DataStorageParamsUtils.toString(new DataStorageParams(EnumsApi.DataSourcing.dispatcher, functionCode)));
            } else {
                DataStorageParams dataStorageParams = DataStorageParamsUtils.to(data.params);
                if (dataStorageParams.sourcing!= EnumsApi.DataSourcing.dispatcher) {
                    // this is an exception for the case when two resources have the same names but different pool codes
                    throw new FunctionDataErrorException(functionCode, "#088.060 Sourcing must be dispatcher, value in db: " + data.getParams());
                }
            }
            data.setUploadTs(new Timestamp(System.currentTimeMillis()));

            Blob blob = em.unwrap(SessionImplementor.class).getLobCreator().createBlob(is, size);
            data.setData(blob);

            functionDataRepository.save(data);

            return data;
        }
        catch(FunctionDataErrorException | PessimisticLockingFailureException e) {
            throw e;
        } catch(Throwable th) {
            String es = "#088.070 error storing data to db - " + th.getMessage();
            log.error(es, th);
            throw new FunctionDataErrorException(functionCode, es);
        }
    }
}
