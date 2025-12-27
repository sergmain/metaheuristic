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

import ai.metaheuristic.ai.dispatcher.beans.CacheVariable;
import ai.metaheuristic.ai.dispatcher.beans.FunctionData;
import ai.metaheuristic.ai.dispatcher.beans.GlobalVariable;
import ai.metaheuristic.ai.dispatcher.beans.VariableBlob;
import ai.metaheuristic.ai.dispatcher.repositories.CacheVariableRepository;
import ai.metaheuristic.ai.dispatcher.repositories.FunctionDataRepository;
import ai.metaheuristic.ai.dispatcher.repositories.GlobalVariableRepository;
import ai.metaheuristic.ai.dispatcher.repositories.VariableBlobRepository;
import ai.metaheuristic.ai.exceptions.FunctionDataErrorException;
import ai.metaheuristic.ai.exceptions.FunctionDataNotFoundException;
import ai.metaheuristic.ai.exceptions.VariableCommonException;
import ai.metaheuristic.commons.yaml.data_storage.DataStorageParamsUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data_storage.DataStorageParams;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.engine.spi.SessionImplementor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.sql.Blob;
import java.sql.Timestamp;

/**
 * @author Sergio Lissner
 * Date: 8/17/2023
 * Time: 12:24 PM
 */
@Slf4j
@Service
@Profile({"dispatcher & !disk-storage"})
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class DatabaseBlobPersistService {

    private final VariableBlobRepository variableBlobRepository;
    private final GlobalVariableRepository globalVariableRepository;
    private final FunctionDataRepository functionDataRepository;
    private final CacheVariableRepository cacheVariableRepository;
    private final EntityManager em;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void storeVariable(Long variableBlobId, InputStream is, long size ) {
        VariableBlob variableBlob = variableBlobRepository.findById(variableBlobId).orElse(null);
        if (variableBlob==null) {
            throw new VariableCommonException("174.040 variableBlob not found", variableBlobId);
        }

        Blob blob = em.unwrap(SessionImplementor.class).getLobCreator().createBlob(is, size);
        variableBlob.setData(blob);
        VariableBlob result = variableBlobRepository.save(variableBlob);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void storeGlobalVariable(Long globalVariableId, InputStream is, long size) {
        GlobalVariable globalVariable = globalVariableRepository.findById(globalVariableId).orElse(null);

        if (globalVariable==null) {
            throw new VariableCommonException("174.080 globalVariable not found", globalVariableId);
        }
        globalVariable.uploadTs = new Timestamp(System.currentTimeMillis());

        Blob blob = em.unwrap(SessionImplementor.class).getLobCreator().createBlob(is, size);
        globalVariable.setData(blob);
        GlobalVariable result = globalVariableRepository.save(globalVariable);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void storeFunctionData(Long functionDataId, InputStream is, long size) {
        FunctionData function = functionDataRepository.findById(functionDataId).orElse(null);
        if (function==null) {
            throw new FunctionDataNotFoundException("id#"+functionDataId, "174.120 function data not found");
        }
        DataStorageParams dataStorageParams = DataStorageParamsUtils.UTILS.to(function.params);
        if (dataStorageParams.sourcing!= EnumsApi.DataSourcing.dispatcher) {
            // this is an exception for the case when two resources have the same names but different pool codes
            throw new FunctionDataErrorException("FunctionData#"+ functionDataId, "174.160 Sourcing must be dispatcher, value in db: " + dataStorageParams.sourcing);
        }

        Blob blob = em.unwrap(SessionImplementor.class).getLobCreator().createBlob(is, size);
        function.setData(blob);
        function.setUploadTs(new Timestamp(System.currentTimeMillis()));

        FunctionData result = functionDataRepository.save(function);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void storeCacheVariableData(Long cacheVariableId, InputStream is, long size) {
        CacheVariable cacheVariable = cacheVariableRepository.findById(cacheVariableId).orElse(null);
        if (cacheVariable==null) {
            throw new FunctionDataNotFoundException("id#"+cacheVariableId, "174.200 cacheVariable not found");
        }
        Blob blob = em.unwrap(SessionImplementor.class).getLobCreator().createBlob(is, size);
        cacheVariable.setData(blob);
        cacheVariable.createdOn = System.currentTimeMillis();
        cacheVariable.nullified = false;

        CacheVariable result = cacheVariableRepository.save(cacheVariable);
    }
}
