/*
 * Metaheuristic, Copyright (C) 2017-2020  Serge Maslyukov
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

import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.dispatcher.beans.GlobalVariable;
import ai.metaheuristic.ai.dispatcher.data.GlobalVariableData;
import ai.metaheuristic.ai.exceptions.VariableSavingException;
import ai.metaheuristic.ai.utils.ControllerUtils;
import ai.metaheuristic.ai.yaml.data_storage.DataStorageParamsUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.OperationStatusRest;
import ai.metaheuristic.api.data_storage.DataStorageParams;
import ai.metaheuristic.commons.utils.DirUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;

@Slf4j
@Profile("dispatcher")
@Service
@RequiredArgsConstructor
public class GlobalVariableTopLevelService {

    private final Globals globals;
    private final GlobalVariableService globalVariableService;

    public GlobalVariableData.GlobalVariablesResult getGlobalVariables(Pageable pageable) {
        pageable = ControllerUtils.fixPageSize(globals.globalVariableRowsLimit, pageable);
        return new GlobalVariableData.GlobalVariablesResult(globalVariableService.getAllAsSimpleGlobalVariable(pageable));
    }

    public OperationStatusRest createGlobalVariableFromFile(MultipartFile file, String variable) {
        return storeInitialGlobalVariable(file, variable, file.getOriginalFilename());
    }

    public OperationStatusRest storeInitialGlobalVariable(MultipartFile file, String variable, @Nullable String originFilename) {
        if (originFilename == null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "#172.020 name of uploaded file is null");
        }
        File tempFile = globals.createTempFileForDispatcher("temp-raw-file-");
        if (tempFile.exists()) {
            if (!tempFile.delete() ) {
                return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                        "#172.030 can't delete dir " + tempFile.getAbsolutePath());
            }
        }
        try {
            try {
                FileUtils.copyInputStreamToFile(file.getInputStream(), tempFile);
            } catch (IOException e) {
                log.error("Error while storing data to temp file", e);
                return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                        "#172.040 can't persist uploaded file as " +
                                tempFile.getAbsolutePath()+", error: " + e.toString());
            }

            try {
                globalVariableService.storeInitialGlobalVariable(tempFile, variable, originFilename);
            } catch (Throwable e) {
                String es = "#172.050 An error while saving data to file, " + e.toString();
                log.error(es, e);
                return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, es);
            }
        } finally {
            DirUtils.deleteAsync(tempFile);
        }
        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    public OperationStatusRest createGlobalVariableWithExternalStorage(String variable, String params ) {

        if (StringUtils.isBlank(params)) {
            String es = "#172.060 GlobalVariable params is blank";
            log.error(es);
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, es);
        }
        DataStorageParams dsp = DataStorageParamsUtils.to(params);
        if (dsp.sourcing==null || dsp.sourcing== EnumsApi.DataSourcing.dispatcher) {
            String es = "#172.070 Sourcing must be "+ EnumsApi.DataSourcing.disk + " or " +EnumsApi.DataSourcing.git +", actual: " + dsp.sourcing;
            log.error(es);
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, es);
        }

        try {
            globalVariableService.createGlobalVariableWithExternalStorage(variable, params);
        } catch (VariableSavingException e) {
            String es = "#172.080 An error while saving variable to db, " + e.toString();
            log.error(es, e);
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, es);
        }
        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    public GlobalVariableData.GlobalVariableResult getGlobalVariableById(Long id) {
        final SimpleGlobalVariable sv = globalVariableService.getByIdAsSimpleGlobalVariable(id);
        if (sv==null) {
            return new GlobalVariableData.GlobalVariableResult("#172.100 Global variable wasn't found for id: " + id);
        }
        return new GlobalVariableData.GlobalVariableResult(sv);
    }

    public OperationStatusRest deleteGlobalVariable(Long id) {
        final GlobalVariable data = globalVariableService.findById(id).orElse(null);
        if (data==null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "#172.120 GlobalVariable wasn't found for id: " + id);
        }
        globalVariableService.deleteById(id);
        return OperationStatusRest.OPERATION_STATUS_OK;
    }
}
