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

package ai.metaheuristic.commons.spi;

import ai.metaheuristic.commons.yaml.task.TaskParamsYaml;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.util.function.Consumer;

/**
 * @author Sergio Lissner
 * Date: 8/17/2023
 * Time: 11:49 AM
 *
 * <p>Error code prefix: {@code 01.170.} (unique to this class).
 */
public interface DispatcherBlobStorage {

    // The application that owns a VariableBlob: the engine's own variables are stored under this value,
    // every other writer passes its own. It is a plain String, not an enum, because the set of writers is
    // open and MH must not carry a list of the applications built on top of it.
    String KIND_MH = "MH";

    // KIND is stored upper-cased. Normalizing here, once, means no backend has to remember to do it and
    // no caller has to shout - and it keeps the stored value canonical whatever case the caller used.
    static String normalizeKind(String kind) {
        if (StringUtils.isBlank(kind)) {
            throw new IllegalStateException("01.170.010 kind of a VariableBlob must not be blank");
        }
        final String normalized = kind.toUpperCase();
        if (normalized.length()>20) {
            throw new IllegalStateException("01.170.020 kind of a VariableBlob is longer than 20 chars: " + normalized);
        }
        return normalized;
    }

    void accessVariableData(final Long variableBlobId, Consumer<InputStream> processBlobDataFunc) throws SQLException, IOException;

    InputStream getVariableDataAsStreamById(Long variableBlobId);

    // 'kind' records WHICH application owns the payload being stored; it is normalized and written onto
    // the VariableBlob row, so a row created as a stub (DEFAULT 'MH') ends up carrying its real owner.
    void storeVariableData(Long variableBlobId, InputStream is, long size, String kind);

    // Immutability (WORM): create the VariableBlob and store its data in one operation, returning the new id.
    // DB backend INSERTs the row with real data (record touched once, no stub); external backends mint a fresh
    // anchor id then write the file/object. Replaces the create-empty + store two-step for the write path.
    Long createAndStoreVariableData(InputStream is, long size, String kind);

    void copyVariableData(StoredVariable sourceVariable, TaskParamsYaml.OutputVariable targetVariable);

    // Release a VariableBlob. Which artifacts actually go away is the BACKEND's decision, not the caller's:
    // a backend whose medium physically guarantees write-once storage implements this as a no-op, because
    // there is nothing it is permitted to remove. Callers must therefore treat this as "I no longer reference
    // this blob", never as "this blob is now gone", and must not assume the id becomes unreadable afterwards.
    void deleteVariableData(Long variableBlobId);


}

