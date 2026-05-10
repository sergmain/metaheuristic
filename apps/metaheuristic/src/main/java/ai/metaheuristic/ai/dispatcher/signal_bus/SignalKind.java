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

package ai.metaheuristic.ai.dispatcher.signal_bus;

/**
 * Signal kinds in v1. Extend by adding enum values and registering a
 * {@link TopicBuilder} + {@link CoalescePolicy} in {@link SignalKindRegistry}.
 */
public enum SignalKind {
    BATCH,
    EXEC_CONTEXT,
    DOCUMENT_EXPORT,
    SYSTEM_NOTICE,

    /**
     * Cross-Project Requirements (Stage 4): emitted when a provider RG
     * project seals a snapshot. Consumer projects that have imported from
     * that provider can pull-discover the new snapshot via
     * {@code RgImportQueryService.findNewerProviderSnapshots}.
     * <p>
     * Topic: {@code provider.snapshot.<providerProjectId>.sealed}.
     * Coalesce policy: NONE — snapshot seals are infrequent enough that
     * each event should reach subscribers individually.
     */
    PROVIDER_SNAPSHOT_SEALED
}
