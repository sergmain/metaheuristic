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
import ai.metaheuristic.ai.dispatcher.beans.VariableBlob;
import ai.metaheuristic.ai.dispatcher.repositories.VariableBlobRepository;
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

/**
 * @author Sergio Lissner
 * Date: 6/7/2023
 * Time: 3:01 PM
 */
@Service
@Slf4j
@Profile({"dispatcher & !disk-storage"})
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class VariableBlobTxService {

    private final VariableBlobRepository variableBlobRepository;
    private final EntityManager em;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long createEmpty() {
        VariableBlob data = new VariableBlob();
        ByteArrayInputStream bais = new ByteArrayInputStream(Consts.STUB_BYTES);
        Blob blob = em.unwrap(SessionImplementor.class).getLobCreator().createBlob(bais, Consts.STUB_BYTES.length);
        data.setData(blob);
        VariableBlob r = variableBlobRepository.save(data);
        return r.id;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long createIfNotExist(@Nullable Long variableBlobId) {
        VariableBlob variableBlob = null;
        if (variableBlobId!=null) {
            variableBlob = variableBlobRepository.findById(variableBlobId).orElse(null);
        }

        if (variableBlob==null) {
            return createEmpty();
        }

        return variableBlob.id;
    }



}
