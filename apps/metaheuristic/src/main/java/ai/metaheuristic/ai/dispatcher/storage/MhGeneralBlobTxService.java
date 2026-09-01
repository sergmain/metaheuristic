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

import ai.metaheuristic.ai.dispatcher.beans.GlobalVariable;
import ai.metaheuristic.ai.dispatcher.beans.VariableBlob;
import ai.metaheuristic.ai.dispatcher.repositories.GlobalVariableRepository;
import ai.metaheuristic.ai.dispatcher.repositories.VariableBlobRepository;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data_storage.DataStorageParams;
import ai.metaheuristic.commons.spi.DispatcherBlobStorage;
import ai.metaheuristic.commons.spi.GeneralBlobTxService;
import ai.metaheuristic.commons.yaml.data_storage.DataStorageParamsUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

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
public class MhGeneralBlobTxService implements GeneralBlobTxService {

    private final VariableBlobRepository variableBlobRepository;
    private final GlobalVariableRepository globalVariableRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Override
    public Long createEmptyVariable(String kind) {
        VariableBlob data = new VariableBlob();
        // WORM: a pre-created record is a stub, not content. The first real store flips this in
        // DatabaseBlobPersistService.storeVariable and closes the record to any further write.
        //
        // DATA is left null rather than seeded with a placeholder. The placeholder existed to keep the
        // column non-null and to be recognisable as "not real content", and IS_MATERIALIZED now records
        // that fact directly. Seeding it was also costly on PostgreSQL, where DATA is an OID: the
        // placeholder allocated a large object that the first real store replaced in the column without
        // unlinking, leaking one object per variable created.
        data.setMaterialized(false);
        // KIND is known here even though the content is not: the caller allocating the stub IS the owner.
        // Recording it at INSERT means an allocated-but-never-materialized row is still attributed to
        // whoever allocated it, rather than defaulting to MH and misreporting the owner.
        data.setKind(DispatcherBlobStorage.normalizeKind(kind));
        VariableBlob r = variableBlobRepository.save(data);
        return r.id;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Override
    public Long createEmptyGlobalVariable(String variable, @Nullable String filename) {
        GlobalVariable data = new GlobalVariable();
        data.name = variable;
        data.filename = filename;
        data.setParams(DataStorageParamsUtils.UTILS.toString(new DataStorageParams(EnumsApi.DataSourcing.dispatcher, variable)));
        data.setUploadTs(new Timestamp(System.currentTimeMillis()));
        // no stub payload: the row starts with no VariableBlob and gains one on the first store
        data.variableBlobId = null;
        GlobalVariable r = globalVariableRepository.save(data);
        return r.id;
    }

}
