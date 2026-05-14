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

package ai.metaheuristic.ai.dispatcher.secret;

import ai.metaheuristic.ai.dispatcher.beans.Processor;
import ai.metaheuristic.ai.dispatcher.processor.ProcessorCache;
import ai.metaheuristic.ai.dispatcher.processor.ProcessorTopLevelService;
import ai.metaheuristic.ai.dispatcher.repositories.ProcessorRepository;
import ai.metaheuristic.ai.dispatcher.vault.VaultEntryChangedEvent;
import ai.metaheuristic.ai.yaml.processor_status.ProcessorStatusYaml;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Per-Processor fan-out queue for Vault invalidations.
 *
 * <p>On {@link VaultEntryChangedEvent}, walks Processors that are currently
 * both <em>active</em> (last keep-alive within
 * {@link ProcessorTopLevelService#PROCESSOR_TIMEOUT}) and <em>enrolled</em>
 * (have a non-null {@code publicKeySpki} on their status YAML), and appends
 * an invalidation entry to each Processor's queue. The keep-alive response
 * builder drains the queue per Processor and embeds the list in
 * {@code KeepAliveResponseParamYaml.vaultInvalidations}.
 *
 * <p>Inactive Processors are skipped — their {@code SealedSecretCache} is
 * either empty or about to be reset on reconnect, so there's nothing to
 * invalidate. This also bounds memory: queues only exist for Processors the
 * Dispatcher considers currently alive.
 *
 * <p>In-memory only — process restart clears state. That's fine: on restart,
 * Processors re-fetch sealed secrets on demand and the cache TTL backstop
 * (1h) covers any window where the Dispatcher missed publishing an event
 * while down.
 *
 * <p>No stale-queue cleanup logic in this stage. Orphan queues for
 * decommissioned Processors are bounded by the number of Vault entries and
 * cleared on Dispatcher restart. Cleanup may be added later, driven by
 * Processor-side ExecContext lifecycle monitoring.
 *
 * @author Sergio Lissner
 */
@Component
@Profile("dispatcher")
@Slf4j
@RequiredArgsConstructor(onConstructor_ = {@Autowired})
public class VaultInvalidationFanout {

    /** One invalidation entry queued for a single Processor. */
    public record Invalidation(long companyId, String keyCode, String action, long ts) {}

    private final ProcessorRepository processorRepository;
    private final ProcessorCache processorCache;

    /** processorId → pending invalidations. */
    private final ConcurrentHashMap<Long, Queue<Invalidation>> queues = new ConcurrentHashMap<>();

    /**
     * Synchronous listener — sub-millisecond fan-out work, no thread offload
     * needed. If profiling later shows {@code putApiKey} latency suffers,
     * switch to {@code @Async @EventListener}.
     *
     * <p>Fans out to Processors that are BOTH:
     * <ul>
     *   <li><strong>active</strong> — last keep-alive within
     *       {@link ProcessorTopLevelService#PROCESSOR_TIMEOUT} (the same
     *       cutoff the rest of the Dispatcher uses), and</li>
     *   <li><strong>enrolled</strong> — has a non-null {@code publicKeySpki}
     *       on its {@link ProcessorStatusYaml}.</li>
     * </ul>
     *
     * <p>An inactive Processor doesn't have anything in its
     * {@code SealedSecretCache} to invalidate — when it reconnects (or is
     * replaced), its cache starts empty and the first task that needs a
     * secret fetches fresh. Queueing for inactive Processors would just leak
     * memory and serve no semantic purpose.
     */
    @EventListener
    public void onVaultEntryChanged(VaultEntryChangedEvent e) {
        long now = System.currentTimeMillis();
        Invalidation entry = new Invalidation(e.companyId(), e.keyCode(), e.action(), now);

        List<Long> processorIds = processorRepository.findAllIds();
        int recipients = 0;
        for (Long processorId : processorIds) {
            Processor p = processorCache.findById(processorId);
            if (p == null) {
                continue;
            }
            // Active check: skip Processors that have not keep-alived recently.
            if (!ProcessorTopLevelService.isActive(p)) {
                continue;
            }
            ProcessorStatusYaml psy = p.getProcessorStatusYaml();
            if (psy.publicKeySpki == null) {
                // Processor hasn't enrolled its public key yet — nothing to invalidate.
                continue;
            }
            queues.computeIfAbsent(processorId, k -> new ConcurrentLinkedQueue<>()).add(entry);
            recipients++;
        }
        log.info("0665.010 VaultEntryChanged companyId={}, keyCode={}, action={} fanned out to {} active Processor(s)",
            e.companyId(), e.keyCode(), e.action(), recipients);
    }

    /**
     * Atomically drain pending invalidations for the given Processor.
     * Returns an empty list when no queue exists or the queue is empty.
     */
    public List<Invalidation> drainFor(long processorId) {
        Queue<Invalidation> q = queues.remove(processorId);
        if (q == null || q.isEmpty()) {
            return List.of();
        }
        List<Invalidation> out = new ArrayList<>(q.size());
        Invalidation item;
        while ((item = q.poll()) != null) {
            out.add(item);
        }
        return out;
    }

    /** Visible for tests/admin: drop all queues. */
    public void clearAll() {
        queues.clear();
    }
}
