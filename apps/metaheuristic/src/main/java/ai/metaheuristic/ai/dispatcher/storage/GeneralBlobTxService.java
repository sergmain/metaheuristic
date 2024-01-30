/*
 * Metaheuristic, Copyright (C) 2017-2024, Innovation platforms, LLC
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

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.dispatcher.beans.CacheVariable;
import ai.metaheuristic.ai.dispatcher.beans.FunctionData;
import ai.metaheuristic.ai.dispatcher.beans.GlobalVariable;
import ai.metaheuristic.ai.dispatcher.beans.VariableBlob;
import ai.metaheuristic.ai.dispatcher.repositories.CacheVariableRepository;
import ai.metaheuristic.ai.dispatcher.repositories.FunctionDataRepository;
import ai.metaheuristic.ai.dispatcher.repositories.GlobalVariableRepository;
import ai.metaheuristic.ai.dispatcher.repositories.VariableBlobRepository;
import ai.metaheuristic.ai.yaml.data_storage.DataStorageParamsUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data_storage.DataStorageParams;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.engine.spi.SessionImplementor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.sql.Blob;
import java.sql.Timestamp;

/**
 * @author Sergio Lissner
 * Date: 6/7/2023
 * Time: 3:01 PM
 */
@Service
@Slf4j
@Profile({"dispatcher"})
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class GeneralBlobTxService {

    private final VariableBlobRepository variableBlobRepository;
    private final GlobalVariableRepository globalVariableRepository;
    private final FunctionDataRepository functionDataRepository;
    private final CacheVariableRepository cacheVariableRepository;
    private final EntityManager em;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long createEmptyVariable() {
        VariableBlob data = new VariableBlob();
        ByteArrayInputStream bais = new ByteArrayInputStream(Consts.STUB_BYTES);
        Blob blob = em.unwrap(SessionImplementor.class).getLobCreator().createBlob(bais, Consts.STUB_BYTES.length);
        data.setData(blob);
        VariableBlob r = variableBlobRepository.save(data);
        return r.id;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long createEmptyGlobalVariable(String variable, @Nullable String filename) {
        GlobalVariable data = new GlobalVariable();
        data.name = variable;
        data.filename = filename;
        data.setParams(DataStorageParamsUtils.toString(new DataStorageParams(EnumsApi.DataSourcing.dispatcher, variable)));
        data.setUploadTs(new Timestamp(System.currentTimeMillis()));

        ByteArrayInputStream bais = new ByteArrayInputStream(Consts.STUB_BYTES);
        Blob blob = em.unwrap(SessionImplementor.class).getLobCreator().createBlob(bais, Consts.STUB_BYTES.length);
        data.setData(blob);
        GlobalVariable r = globalVariableRepository.save(data);
        return r.id;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long createEmptyFunctionData(String functionCode) {
        FunctionData data = new FunctionData();
        data.functionCode = functionCode;
        data.uploadTs = new Timestamp(System.currentTimeMillis());
        data.setParams(DataStorageParamsUtils.toString(new DataStorageParams(EnumsApi.DataSourcing.dispatcher, functionCode)));

        ByteArrayInputStream bais = new ByteArrayInputStream(Consts.STUB_BYTES);
        Blob blob = em.unwrap(SessionImplementor.class).getLobCreator().createBlob(bais, Consts.STUB_BYTES.length);
        data.setData(blob);

        FunctionData r = functionDataRepository.save(data);
        return r.id;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CacheVariable createEmptyCacheVariable(Long cacheProcessId, String variable) {
        //TxUtils.checkTxExists();

        CacheVariable data = new CacheVariable();
        data.cacheProcessId = cacheProcessId;
        data.variableName = variable;
        data.createdOn = System.currentTimeMillis();
        data.data = null;
        data.nullified = true;

        data = cacheVariableRepository.save(data);

        return data;
    }


}
