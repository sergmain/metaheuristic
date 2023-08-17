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

package ai.metaheuristic.ai.dispatcher.storage;

import ai.metaheuristic.ai.dispatcher.beans.VariableBlob;
import ai.metaheuristic.ai.dispatcher.repositories.VariableBlobRepository;
import ai.metaheuristic.ai.exceptions.VariableCommonException;
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
    private final EntityManager em;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void storeData(Long variableBlobId, InputStream is, long size ) {
        VariableBlob variableBlob = null;
        if (variableBlobId!=null) {
            variableBlob = variableBlobRepository.findById(variableBlobId).orElse(null);
        }

        if (variableBlob==null) {
            throw new VariableCommonException("174.040 variableBlob not found", variableBlobId);
        }

        Blob blob = em.unwrap(SessionImplementor.class).getLobCreator().createBlob(is, size);
        variableBlob.setData(blob);
        VariableBlob result = variableBlobRepository.save(variableBlob);
    }
}
