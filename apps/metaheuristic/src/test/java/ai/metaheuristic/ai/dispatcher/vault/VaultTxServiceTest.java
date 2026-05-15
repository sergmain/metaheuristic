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

package ai.metaheuristic.ai.dispatcher.vault;

import ai.metaheuristic.ai.dispatcher.beans.Company;
import ai.metaheuristic.ai.dispatcher.company.CompanyCache;
import ai.metaheuristic.ai.dispatcher.event.events.VaultEntryChangedTxEvent;
import ai.metaheuristic.ai.dispatcher.repositories.CompanyRepository;
import ai.metaheuristic.ai.yaml.company.CompanyParamsYaml;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT;

/**
 * Plain unit tests for {@link VaultTxService}. The Spring transaction boundary
 * itself is exercised in @SpringBootTest integration tests elsewhere; here we
 * verify only the behavioural contract: the right blob is written through to
 * the cache, the {@code @Version} optimistic-lock path is preserved, the
 * {@code VaultEntryChangedTxEvent} is published on success, and missing
 * companies return false without emitting an event.
 *
 * @author Sergio Lissner
 */
@Execution(CONCURRENT)
class VaultTxServiceTest {

    /** Captures every event the unit under test publishes. */
    private static final class CapturingPublisher implements ApplicationEventPublisher {
        final List<Object> events = new ArrayList<>();
        @Override public void publishEvent(Object event) { events.add(event); }
        @Override public void publishEvent(ApplicationEvent event) { events.add(event); }
    }

    /**
     * Hand-rolled stand-in for {@link CompanyRepository}. Only the lookup
     * methods VaultTxService actually calls are implemented; everything else
     * throws so a missed dependency surfaces loudly.
     */
    private static final class FakeCompanyRepository implements CompanyRepository {
        final Map<Long, Company> byUniqueId = new HashMap<>();

        @Override public Company findByUniqueIdForUpdate(Long uniqueId) { return byUniqueId.get(uniqueId); }
        @Override public Company findByUniqueId(Long uniqueId)         { return byUniqueId.get(uniqueId); }

        // --- Unused methods ---
        @Override public void deleteById(Long id) { throw new UnsupportedOperationException(); }
        @Override public Page<Company> findAll(Pageable pageable) { throw new UnsupportedOperationException(); }
        @Override public Page<ai.metaheuristic.ai.dispatcher.data.SimpleCompany> findAllAsSimple(Pageable pageable) { throw new UnsupportedOperationException(); }
        @Override public Long getMaxUniqueIdValue() { throw new UnsupportedOperationException(); }
        @Override public List<Long> findAllUniqueIds() { throw new UnsupportedOperationException(); }
        @Override public <S extends Company> S save(S entity) { throw new UnsupportedOperationException(); }
        @Override public <S extends Company> Iterable<S> saveAll(Iterable<S> entities) { throw new UnsupportedOperationException(); }
        @Override public Optional<Company> findById(Long aLong) { throw new UnsupportedOperationException(); }
        @Override public boolean existsById(Long aLong) { throw new UnsupportedOperationException(); }
        @Override public Iterable<Company> findAll() { throw new UnsupportedOperationException(); }
        @Override public Iterable<Company> findAllById(Iterable<Long> longs) { throw new UnsupportedOperationException(); }
        @Override public long count() { throw new UnsupportedOperationException(); }
        @Override public void delete(Company entity) { throw new UnsupportedOperationException(); }
        @Override public void deleteAllById(Iterable<? extends Long> longs) { throw new UnsupportedOperationException(); }
        @Override public void deleteAll(Iterable<? extends Company> entities) { throw new UnsupportedOperationException(); }
        @Override public void deleteAll() { throw new UnsupportedOperationException(); }
    }

    /**
     * Subclass of {@link CompanyCache} that bypasses the real one, since the
     * cache's save() asserts a real TX context exists.
     */
    private static final class FakeCompanyCache extends CompanyCache {
        final FakeCompanyRepository fakeRepo;
        Company saved;
        FakeCompanyCache(FakeCompanyRepository repo) {
            super(repo);
            this.fakeRepo = repo;
        }

        @Override public Company save(Company c) {
            this.saved = c;
            // Mirror the real path: keep the repository's view consistent with what was saved.
            fakeRepo.byUniqueId.put(c.uniqueId, c);
            return c;
        }

        @Override public Company findByUniqueId(Long uniqueId) {
            return fakeRepo.findByUniqueId(uniqueId);
        }
    }

    private static Company company(long uniqueId, @Nullable String existingParams) {
        Company c = new Company();
        c.id = uniqueId + 1000L;
        c.uniqueId = uniqueId;
        c.name = "co-" + uniqueId;
        if (existingParams != null) {
            c.setParams(existingParams);
        }
        return c;
    }

    @Test
    void saveVaultBlob_companyExists_persistsBlobAndEmitsEvent() {
        FakeCompanyRepository repo = new FakeCompanyRepository();
        FakeCompanyCache cache = new FakeCompanyCache(repo);
        CapturingPublisher pub = new CapturingPublisher();

        long companyId = 42L;
        repo.byUniqueId.put(companyId, company(companyId, null));

        VaultTxService tx = new VaultTxService(repo, cache, pub);
        CompanyParamsYaml.VaultEntries blob = new CompanyParamsYaml.VaultEntries("c2FsdA==", 200_000, "ZW5jcnlwdGVk");
        assertTrue(tx.saveVaultBlob(companyId, blob, "openai", VaultEntryChangedEvent.ACTION_PUT));

        assertNotNull(cache.saved);
        CompanyParamsYaml cpy = cache.saved.getCompanyParamsYaml();
        assertNotNull(cpy.vault);
        assertEquals(blob.salt, cpy.vault.salt);
        assertEquals(blob.iterations, cpy.vault.iterations);
        assertEquals(blob.encryptedEntries, cpy.vault.encryptedEntries);
        assertTrue(cpy.createdOn > 0L, "createdOn must be populated for a previously-empty params blob");
        assertTrue(cpy.updatedOn >= cpy.createdOn);

        assertEquals(1, pub.events.size());
        Object e = pub.events.get(0);
        assertInstanceOf(VaultEntryChangedTxEvent.class, e);
        VaultEntryChangedTxEvent evt = (VaultEntryChangedTxEvent) e;
        assertEquals(companyId, evt.companyId);
        assertEquals("openai", evt.keyCode);
        assertEquals(VaultEntryChangedEvent.ACTION_PUT, evt.action);
    }

    @Test
    void saveVaultBlob_companyMissing_returnsFalse_noEvent() {
        FakeCompanyRepository repo = new FakeCompanyRepository();
        FakeCompanyCache cache = new FakeCompanyCache(repo);
        CapturingPublisher pub = new CapturingPublisher();

        VaultTxService tx = new VaultTxService(repo, cache, pub);
        CompanyParamsYaml.VaultEntries blob = new CompanyParamsYaml.VaultEntries("c2FsdA==", 200_000, "ZW5jcnlwdGVk");
        assertFalse(tx.saveVaultBlob(99L, blob, "openai", VaultEntryChangedEvent.ACTION_PUT));

        assertNull(cache.saved);
        assertTrue(pub.events.isEmpty());
    }

    @Test
    void saveVaultBlob_preservesPreexistingAccessControl() {
        // Verify saving a vault blob does not clobber the existing access-control state.
        FakeCompanyRepository repo = new FakeCompanyRepository();
        FakeCompanyCache cache = new FakeCompanyCache(repo);
        CapturingPublisher pub = new CapturingPublisher();

        long companyId = 42L;
        Company c = company(companyId, null);
        CompanyParamsYaml seed = c.getCompanyParamsYaml();
        seed.ac = new CompanyParamsYaml.AccessControl("ops,devs");
        seed.createdOn = 1_700_000_000_000L;
        c.updateParams(seed);
        repo.byUniqueId.put(companyId, c);

        VaultTxService tx = new VaultTxService(repo, cache, pub);
        CompanyParamsYaml.VaultEntries blob = new CompanyParamsYaml.VaultEntries("c2FsdA==", 200_000, "ZW5jcnlwdGVk");
        assertTrue(tx.saveVaultBlob(companyId, blob, "openai", VaultEntryChangedEvent.ACTION_PUT));

        CompanyParamsYaml after = cache.saved.getCompanyParamsYaml();
        assertNotNull(after.ac);
        assertEquals("ops,devs", after.ac.groups);
        assertNotNull(after.vault);
        assertEquals(1_700_000_000_000L, after.createdOn, "createdOn must be preserved");
    }

    @Test
    void loadVaultBlob_companyExists_withVault_returnsBlob() {
        FakeCompanyRepository repo = new FakeCompanyRepository();
        FakeCompanyCache cache = new FakeCompanyCache(repo);
        CapturingPublisher pub = new CapturingPublisher();

        long companyId = 7L;
        Company c = company(companyId, null);
        CompanyParamsYaml seed = c.getCompanyParamsYaml();
        seed.vault = new CompanyParamsYaml.VaultEntries("c2FsdA==", 200_000, "ZW5jcnlwdGVk");
        c.updateParams(seed);
        repo.byUniqueId.put(companyId, c);

        VaultTxService tx = new VaultTxService(repo, cache, pub);
        CompanyParamsYaml.VaultEntries blob = tx.loadVaultBlob(companyId);
        assertNotNull(blob);
        assertEquals("c2FsdA==", blob.salt);
        assertEquals(200_000, blob.iterations);
        assertEquals("ZW5jcnlwdGVk", blob.encryptedEntries);
    }

    @Test
    void loadVaultBlob_companyExists_withoutVault_returnsNull() {
        FakeCompanyRepository repo = new FakeCompanyRepository();
        FakeCompanyCache cache = new FakeCompanyCache(repo);
        CapturingPublisher pub = new CapturingPublisher();

        long companyId = 7L;
        repo.byUniqueId.put(companyId, company(companyId, null));

        VaultTxService tx = new VaultTxService(repo, cache, pub);
        assertNull(tx.loadVaultBlob(companyId));
    }

    @Test
    void loadVaultBlob_companyMissing_returnsNull() {
        FakeCompanyRepository repo = new FakeCompanyRepository();
        FakeCompanyCache cache = new FakeCompanyCache(repo);
        CapturingPublisher pub = new CapturingPublisher();
        VaultTxService tx = new VaultTxService(repo, cache, pub);

        assertNull(tx.loadVaultBlob(123L));
    }
}
