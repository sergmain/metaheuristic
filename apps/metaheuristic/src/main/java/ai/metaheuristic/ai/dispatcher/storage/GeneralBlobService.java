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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * @author Sergio Lissner
 * Date: 10/20/2023
 * Time: 1:55 PM
 */
@Service
@Slf4j
@Profile({"dispatcher"})
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class GeneralBlobService {

    private final GeneralBlobTxService generalBlobTxService;
    private final VariableBlobRepository variableBlobRepository;

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Long createVariableIfNotExist(@Nullable Long variableBlobId) {
        VariableBlob variableBlob = null;
        if (variableBlobId!=null) {
            variableBlob = variableBlobRepository.findById(variableBlobId).orElse(null);
        }
        if (variableBlob != null) {
            return variableBlobId;
        }

        return generalBlobTxService.createEmptyVariable();
    }
}
