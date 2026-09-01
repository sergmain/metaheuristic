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
package ai.metaheuristic.ai.dispatcher.function;

import ai.metaheuristic.ai.dispatcher.beans.Function;
import ai.metaheuristic.ai.dispatcher.repositories.FunctionRepository;
import ai.metaheuristic.ai.exceptions.FunctionDataErrorException;
import ai.metaheuristic.commons.spi.DispatcherBlobStorage;
import ai.metaheuristic.commons.spi.GeneralBlobTxService;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.commons.yaml.function.FunctionConfigYaml;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static ai.metaheuristic.api.EnumsApi.FunctionSourcing.dispatcher;

/**
 * <p>Error code prefix: {@code 01.297.} (unique to this class).
 */
@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class FunctionTxService {

    private final FunctionCache functionCache;
    private final FunctionRepository functionRepository;
    private final FunctionDataTxService functionDataService;
    private final GeneralBlobTxService generalBlobTxService;
    private final DispatcherBlobStorage dispatcherBlobStorage;

    @Transactional
    public void deleteFunction(Long functionId) {
        // read the anchor BEFORE the row goes: once the Function is deleted nothing knows which
        // VariableBlob belonged to it, and the blob would be unreachable rather than released
        final Function function = functionCache.findById(functionId);
        final Long variableBlobId = function==null ? null : function.variableBlobId;
        functionCache.delete(functionId);
        if (variableBlobId!=null) {
            dispatcherBlobStorage.deleteVariableData(variableBlobId);
        }
    }

    @Transactional
    public Function persistFunction(FunctionConfigYaml functionConfig, @Nullable InputStream inputStream, long size) {
        Function function = new Function();
        function.code = functionConfig.function.code;
        function.type = functionConfig.function.type!=null ? functionConfig.function.type : "";

        // the payload description used to be written onto the FunctionData row by
        // createEmptyFunctionData; it belongs to the Function itself, so it is set here instead.
        // A copy is made because the caller keeps using its own FunctionConfigYaml afterwards.
        // No inputStream means no dispatcher-held bytes at all - a git-sourced Function - and then
        // there is no payload to describe, hence null rather than an empty DataStorage.
        final FunctionConfigYaml fcy = functionConfig.clone();
        fcy.dataStorage = inputStream==null
                ? null
                : new FunctionConfigYaml.DataStorage(EnumsApi.DataSourcing.dispatcher, functionConfig.function.code);
        function.updateParams(fcy);

        String functionCode = function.getCode();
        if (inputStream!=null) {
            // this is an exception for the case when two resources have the same names but different pool codes
            if (fcy.dataStorage==null || fcy.dataStorage.sourcing!=EnumsApi.DataSourcing.dispatcher) {
                throw new FunctionDataErrorException(functionCode,
                        "01.297.020 Sourcing must be dispatcher, actual: " + (fcy.dataStorage==null ? null : fcy.dataStorage.sourcing));
            }
            // the payload goes into an ordinary VariableBlob and the Function keeps its id. The blob is
            // written before the Function row is saved so the anchor is never persisted pointing at
            // nothing - createEmptyVariable and the store both run in their own REQUIRES_NEW tx.
            final Long variableBlobId = generalBlobTxService.createEmptyVariable(DispatcherBlobStorage.KIND_MH);
            dispatcherBlobStorage.storeVariableData(variableBlobId, inputStream, size, DispatcherBlobStorage.KIND_MH);
            function.variableBlobId = variableBlobId;
        }
        function = functionCache.save(function);
        return function;
    }

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public List<Pair<EnumsApi.FunctionSourcing, String>> collectInfoAboutFunction1() {
        try (Stream<Function> stream = functionRepository.findAllAsStream()) {
            return stream
                .map(function -> {
                    FunctionConfigYaml fcy = function.getFunctionConfigYaml();
                    return Pair.of(fcy.function.sourcing==null ? dispatcher : fcy.function.sourcing, function.code);
                })
                .collect(Collectors.toList());
        }
    }

}
