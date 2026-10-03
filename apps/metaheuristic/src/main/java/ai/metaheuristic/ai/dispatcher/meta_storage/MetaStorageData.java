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

package ai.metaheuristic.ai.dispatcher.meta_storage;

import org.jspecify.annotations.Nullable;

/**
 * Small-scoped carriers between the orchestrator and the tx service.
 *
 * @author Serge
 */
public class MetaStorageData {

    /**
     * One record as seen by a caller. {@code body} is opaque to MH.
     */
    public record Record(String type, String recKey, String body) {}

    /**
     * A resolved write: the orchestrator has already looked up whether the row exists, so the tx
     * method receives the decision instead of making it.
     *
     * <p>Per SPRING-TX-RULES.md §1, an existence check belongs in the non-transactional orchestrator
     * BEFORE the tx is opened - a {@code @Transactional} method assumes its inputs are validated and
     * touches only what it updates. {@code existingId} is that resolution, carried in at the
     * smallest possible scope.
     */
    public record ResolvedWrite(@Nullable Long existingId, Record record) {}

    /**
     * One meta table, identified the only way a meta table can be identified.
     *
     * <p>A type is not a row anywhere - it exists by virtue of records carrying it - so enumerating
     * the tables a store holds means grouping it by {@code (COMPANY_ID, TYPE)}. ❗ The PAIR is the
     * identity, not the name: the store is partitioned by company, so the same name under two
     * companies is two tables holding two unrelated sets of records, and a listing that collapsed
     * them would be reporting something that does not exist.
     *
     * <p>Used as a JPQL constructor target by the cross-company enumerations on
     * {@code MetaStorageRepository} and {@code MetaStorageSyntheticRepository}.
     */
    public record TypeRef(Long companyId, String type) {}

    /**
     * One meta table together with when it was last written to - the latest {@code UPDATED_AT} over
     * its records.
     *
     * <p>Kept apart from {@link TypeRef} on purpose: TypeRef is the IDENTITY of a table and is used as
     * a map key, and a timestamp inside it would make two references to the same table unequal.
     *
     * <p>{@code lastUpdatedAt} is a record-level fact, so - unlike the registry's CREATED_ON - every
     * listed table has one: a table exists only by virtue of its records, and every record carries
     * {@code UPDATED_AT}. ⚠️ It is the time of the latest WRITE, not of the latest change of content:
     * an upsert that stores the same body again moves it forward too.
     *
     * <p>Used as a JPQL constructor target - {@code max(...)} of a {@code long} attribute arrives as a
     * {@code Long}, hence the boxed type.
     */
    public record TypeStat(Long companyId, String type, Long lastUpdatedAt) {
        public TypeRef ref() {
            return new TypeRef(companyId, type);
        }
    }
}
