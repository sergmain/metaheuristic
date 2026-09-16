/*
 * Metaheuristic, Copyright (C) 2017-2026, Innovation platforms, LLC
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
package ai.metaheuristic.api.data.meta_storage;

import ai.metaheuristic.api.data.BaseParams;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

/**
 * <b>!!! BEFORE MAKING ANY EDITION IN THIS CLASS, READ metaheuristic.wiki/p/multi-versioning-mechanic.md</b>
 * <br/>
 * Version-less (current) schema of {@code MH_META_STORAGE_REGISTRY.PARAMS} - what ONE meta storage
 * table is for. Must remain field-for-field identical to the latest versioned class
 * ({@link MetaStorageRegistryParamsV1}); business logic only ever sees this one.
 *
 * <p>Why the registry exists at all: a meta storage TYPE is an opaque string, and listing the types a
 * company holds answers with names like {@code mh.asset.dir-batch-for-requirements.12}. A name carries
 * a producer and a run id and nothing else - not what is inside, not how to read a record, not whether
 * anyone still needs it. Without a descriptor the only way to find out is to pull a record and guess
 * from its body, and the only way to judge staleness is to find someone who remembers the run.
 *
 * <p>WHAT IS NOT HERE, DELIBERATELY. The described table's name lives in the {@code META_TABLE} column,
 * which store it belongs to lives in {@code PROD}, and when it was registered lives in
 * {@code CREATED_ON}. None of the three is repeated in this payload: a fact held in both a column and
 * a serialized blob is a fact that can disagree with itself, and the column is the one the unique
 * index and every query already use.
 *
 * @author Serge
 */
@Data
@NoArgsConstructor
public class MetaStorageRegistryParams implements BaseParams {

    public final int version = 1;

    /** One sentence, for a human who was not there when the run happened. */
    public String desc;

    /** The SourceCode uid that wrote the table, so the graph behind it can be read back. */
    public String producer;

    /**
     * How the recKeys are shaped, so a consumer orders and parses them instead of inferring the shape
     * from one sample - e.g. {@code batch-NNNN, 1-based, zero-padded to the width of the batch count}.
     */
    public String recKeyFormat;

    /**
     * How one record's body is encoded, so it is read rather than guessed at. MH never parsed or
     * validated a body, so nothing else in the system can answer this.
     */
    public String bodyFormat;

    /**
     * The ExecContext that produced the table. Also how to check whether that run FINISHED.
     * Null when the table was not written by a run - a hand-seeded table, a migration.
     */
    @Nullable
    public Long execContextId;

    /** The Function code that did the writing, where one Function serves several SourceCodes. */
    @Nullable
    public String function;

    /** The consumer protocol in one line, e.g. "list keys, take one, do the work, delete it". */
    @Nullable
    public String consumer;
}