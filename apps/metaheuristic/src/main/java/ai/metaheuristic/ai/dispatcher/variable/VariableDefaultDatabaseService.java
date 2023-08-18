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

package ai.metaheuristic.ai.dispatcher.variable;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.dispatcher.beans.Variable;
import ai.metaheuristic.ai.dispatcher.data.VariableData;
import ai.metaheuristic.ai.dispatcher.event.events.ResourceCloseTxEvent;
import ai.metaheuristic.ai.dispatcher.repositories.VariableRepository;
import ai.metaheuristic.ai.dispatcher.storage.DatabaseBlobPersistService;
import ai.metaheuristic.ai.dispatcher.storage.DispatcherBlobStorage;
import ai.metaheuristic.ai.dispatcher.storage.GeneralBlobTxService;
import ai.metaheuristic.ai.exceptions.BreakFromLambdaException;
import ai.metaheuristic.ai.exceptions.CommonErrorWithDataException;
import ai.metaheuristic.ai.exceptions.VariableDataNotFoundException;
import ai.metaheuristic.ai.utils.TxUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.commons.utils.DirUtils;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;

/**
 * @author Serge
 * Date: 12/22/2021
 * Time: 10:45 PM
 */

// https://stackoverflow.com/questions/35252262/spring-boot-configuration-skip-registration-on-multiple-profile/59631429#59631429
@SuppressWarnings("DuplicatedCode")
@Service
@Slf4j
@Profile({"dispatcher & !mysql & !postgresql"})
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class VariableDefaultDatabaseService implements VariableDatabaseSpecificService {

    private final Globals globals;
    private final GeneralBlobTxService generalBlobTxService;
    private final ApplicationEventPublisher eventPublisher;
    private final DatabaseBlobPersistService databaseBlobPersistService;
    private final VariableRepository variableRepository;
    private final DispatcherBlobStorage dispatcherBlobStorage;

    @SneakyThrows
    public void copyData(VariableData.StoredVariable srcVariable, TaskParamsYaml.OutputVariable targetVariable) {
        TxUtils.checkTxExists();

        final Path tempFile;
        try {
            tempFile = Files.createTempFile(globals.dispatcherTempPath, "var-" + srcVariable.id + "-", Consts.BIN_EXT);
        }
        catch (IOException e) {
            throw new BreakFromLambdaException(e.getMessage());
        }
        InputStream is;
        try {
            // TODO 2021-10-14 right now, an array variable isn't supported
            dispatcherBlobStorage.accessCacheVariableData(srcVariable.id, (inputStream)-> DirUtils.copy(inputStream, tempFile));
            is = Files.newInputStream(tempFile);
        } catch (CommonErrorWithDataException e) {
            eventPublisher.publishEvent(new ResourceCloseTxEvent(tempFile));
            throw e;
        } catch (Exception e) {
            eventPublisher.publishEvent(new ResourceCloseTxEvent(tempFile));
            String es = "#173.040 Error while storing data to file";
            log.error(es, e);
            throw new IllegalStateException(es, e);
        }
        eventPublisher.publishEvent(new ResourceCloseTxEvent(is, tempFile));
        storeData(is, Files.size(tempFile), targetVariable.id, targetVariable.filename);
    }

    private void storeData(InputStream is, long size, Long variableId, @Nullable String filename) {
        VariableSyncService.checkWriteLockPresent(variableId);

        if (size==0) {
            throw new IllegalStateException("171.720 Variable can't be with zero length");
        }
        TxUtils.checkTxExists();

        Variable data = getVariableNotNull(variableId);

        data.filename = filename;
        data.setUploadTs(new Timestamp(System.currentTimeMillis()));

        data.variableBlobId = generalBlobTxService.createVariableIfNotExist(data.variableBlobId);
        databaseBlobPersistService.storeVariable(data.variableBlobId, is, size);

        data.inited = true;
        data.nullified = false;

        variableRepository.save(data);
    }

    private Variable getVariableNotNull(Long variableId) {
        Variable v = variableRepository.findByIdAsSimple(variableId);
        if (v==null) {
            String es = "171.535 Variable #" + variableId + " wasn't found";
            log.warn(es);
            throw new VariableDataNotFoundException(variableId, EnumsApi.VariableContext.local, es);
        }
        return v;
    }
}
