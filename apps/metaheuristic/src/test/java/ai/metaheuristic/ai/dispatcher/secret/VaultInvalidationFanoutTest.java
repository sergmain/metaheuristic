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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link VaultInvalidationFanout}.
 *
 * <p>Pure Mockito unit tests — no Spring context. The fanout listener is
 * exercised by calling {@code onVaultEntryChanged} directly with a synthetic
 * event; the {@code @EventListener} wiring is Spring's responsibility and
 * not under test here.
 *
 * @author Sergio Lissner
 */
@Execution(CONCURRENT)
class VaultInvalidationFanoutTest {

    @Test
    void test_fanout_activeAndEnrolled_isQueued() {
        ProcessorRepository repo = mock(ProcessorRepository.class);
        ProcessorCache cache = mock(ProcessorCache.class);
        when(repo.findAllIds()).thenReturn(List.of(42L));
        when(cache.findById(42L)).thenReturn(activeEnrolledProcessor(42L));

        VaultInvalidationFanout fanout = new VaultInvalidationFanout(repo, cache);
        fanout.onVaultEntryChanged(new VaultEntryChangedEvent(
            7L, "openai_api_key", VaultEntryChangedEvent.ACTION_PUT));

        List<VaultInvalidationFanout.Invalidation> drained = fanout.drainFor(42L);
        assertEquals(1, drained.size());
        assertEquals(7L, drained.get(0).companyId());
        assertEquals("openai_api_key", drained.get(0).keyCode());
        assertEquals("put", drained.get(0).action());
    }

    @Test
    void test_fanout_inactive_isSkipped() {
        ProcessorRepository repo = mock(ProcessorRepository.class);
        ProcessorCache cache = mock(ProcessorCache.class);
        when(repo.findAllIds()).thenReturn(List.of(42L));
        // Processor is enrolled (publicKeySpki set) but inactive (updatedOn far in the past).
        when(cache.findById(42L)).thenReturn(inactiveEnrolledProcessor(42L));

        VaultInvalidationFanout fanout = new VaultInvalidationFanout(repo, cache);
        fanout.onVaultEntryChanged(new VaultEntryChangedEvent(
            7L, "openai_api_key", VaultEntryChangedEvent.ACTION_PUT));

        assertTrue(fanout.drainFor(42L).isEmpty());
    }

    @Test
    void test_fanout_notEnrolled_isSkipped() {
        ProcessorRepository repo = mock(ProcessorRepository.class);
        ProcessorCache cache = mock(ProcessorCache.class);
        when(repo.findAllIds()).thenReturn(List.of(42L));
        // Processor is active but has not enrolled a public key yet.
        when(cache.findById(42L)).thenReturn(activeNotEnrolledProcessor(42L));

        VaultInvalidationFanout fanout = new VaultInvalidationFanout(repo, cache);
        fanout.onVaultEntryChanged(new VaultEntryChangedEvent(
            7L, "openai_api_key", VaultEntryChangedEvent.ACTION_PUT));

        assertTrue(fanout.drainFor(42L).isEmpty());
    }

    @Test
    void test_drainFor_isAtomic_emptiesQueueOnDrain() {
        ProcessorRepository repo = mock(ProcessorRepository.class);
        ProcessorCache cache = mock(ProcessorCache.class);
        when(repo.findAllIds()).thenReturn(List.of(42L));
        when(cache.findById(42L)).thenReturn(activeEnrolledProcessor(42L));

        VaultInvalidationFanout fanout = new VaultInvalidationFanout(repo, cache);
        fanout.onVaultEntryChanged(new VaultEntryChangedEvent(7L, "k1", VaultEntryChangedEvent.ACTION_PUT));
        fanout.onVaultEntryChanged(new VaultEntryChangedEvent(7L, "k2", VaultEntryChangedEvent.ACTION_DELETE));

        List<VaultInvalidationFanout.Invalidation> first = fanout.drainFor(42L);
        assertEquals(2, first.size());

        // Second drain must be empty — first one consumed everything.
        List<VaultInvalidationFanout.Invalidation> second = fanout.drainFor(42L);
        assertTrue(second.isEmpty());
    }

    @Test
    void test_fanout_mixOfProcessors_onlyActiveEnrolledGetEntries() {
        ProcessorRepository repo = mock(ProcessorRepository.class);
        ProcessorCache cache = mock(ProcessorCache.class);
        when(repo.findAllIds()).thenReturn(List.of(42L, 43L, 44L));
        when(cache.findById(42L)).thenReturn(activeEnrolledProcessor(42L));
        when(cache.findById(43L)).thenReturn(inactiveEnrolledProcessor(43L));
        when(cache.findById(44L)).thenReturn(activeNotEnrolledProcessor(44L));

        VaultInvalidationFanout fanout = new VaultInvalidationFanout(repo, cache);
        fanout.onVaultEntryChanged(new VaultEntryChangedEvent(7L, "k", VaultEntryChangedEvent.ACTION_PUT));

        assertEquals(1, fanout.drainFor(42L).size());
        assertTrue(fanout.drainFor(43L).isEmpty());
        assertTrue(fanout.drainFor(44L).isEmpty());
    }

    // ---- helpers -----------------------------------------------------------

    private static Processor activeEnrolledProcessor(long id) {
        Processor p = new Processor();
        p.id = id;
        p.updatedOn = System.currentTimeMillis();
        ProcessorStatusYaml psy = newStatusYaml();
        psy.publicKeySpki = "spki-base64";
        psy.keyFingerprint = "fp-hex";
        p.updateParams(psy);
        return p;
    }

    private static Processor inactiveEnrolledProcessor(long id) {
        Processor p = new Processor();
        p.id = id;
        // Far past — well beyond PROCESSOR_TIMEOUT (140s).
        p.updatedOn = System.currentTimeMillis() - ProcessorTopLevelService.PROCESSOR_TIMEOUT - 1_000;
        ProcessorStatusYaml psy = newStatusYaml();
        psy.publicKeySpki = "spki-base64";
        psy.keyFingerprint = "fp-hex";
        p.updateParams(psy);
        return p;
    }

    private static Processor activeNotEnrolledProcessor(long id) {
        Processor p = new Processor();
        p.id = id;
        p.updatedOn = System.currentTimeMillis();
        ProcessorStatusYaml psy = newStatusYaml();
        // publicKeySpki stays null — Processor hasn't completed Stage 4 enrollment.
        p.updateParams(psy);
        return p;
    }

    private static ProcessorStatusYaml newStatusYaml() {
        ProcessorStatusYaml psy = new ProcessorStatusYaml();
        psy.sessionId = "sess";
        psy.sessionCreatedOn = 0L;
        psy.ip = "0.0.0.0";
        psy.host = "h";
        psy.schedule = "";
        psy.logDownloadable = false;
        psy.taskParamsVersion = 0;
        psy.currDir = ".";
        return psy;
    }
}
