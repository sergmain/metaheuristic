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
import ai.metaheuristic.ai.dispatcher.beans.CompanyRevision;
import ai.metaheuristic.ai.dispatcher.company.CompanyCache;
import ai.metaheuristic.ai.dispatcher.company.CompanyRevisionWriter;
import ai.metaheuristic.ai.dispatcher.event.events.VaultEntryChangedTxEvent;
import ai.metaheuristic.ai.dispatcher.repositories.CompanyRepository;
import ai.metaheuristic.ai.dispatcher.repositories.CompanyRevisionRepository;
import ai.metaheuristic.ai.yaml.company.CompanyParamsYaml;
import ai.metaheuristic.ai.yaml.company.CompanyParamsYamlUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT;

/**
 * Plain unit tests for {@link VaultTxService}. The Spring transaction boundary
 * itself is exercised in @SpringBootTest integration tests elsewhere; here we
 * verify only the behavioural contract: the right blob is written into a
 * new {@link CompanyRevision} via {@link CompanyRevisionWriter}, the
 * {@link VaultEntryChangedTxEvent} is published on success, and missing
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
        final Map<Long, Company> byId = new HashMap<>();

        void put(Company c) {
            byUniqueId.put(c.uniqueId, c);
            byId.put(c.id, c);
        }

        @Override public Company findByUniqueIdForUpdate(Long uniqueId) { return byUniqueId.get(uniqueId); }
        @Override public Company findByUniqueId(Long uniqueId)         { return byUniqueId.get(uniqueId); }
        @Override public Optional<Company> findById(Long id)            { return Optional.ofNullable(byId.get(id)); }
        @Override public <S extends Company> S save(S entity) {
            byId.put(entity.id, entity);
            byUniqueId.put(entity.uniqueId, entity);
            return entity;
        }

        // --- Unused methods ---
        @Override public void deleteById(Long id) { throw new UnsupportedOperationException(); }
        @Override public Page<Company> findAll(Pageable pageable) { throw new UnsupportedOperationException(); }
        @Override public Page<ai.metaheuristic.ai.dispatcher.data.SimpleCompany> findAllAsSimple(Pageable pageable) { throw new UnsupportedOperationException(); }
        @Override public Long getMaxUniqueIdValue() { throw new UnsupportedOperationException(); }
        @Override public List<Long> findAllUniqueIds() { throw new UnsupportedOperationException(); }
        @Override public <S extends Company> Iterable<S> saveAll(Iterable<S> entities) { throw new UnsupportedOperationException(); }
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
     * Hand-rolled stand-in for {@link CompanyRevisionRepository}. Tracks
     * inserted revision rows keyed by id, plus the max revision number
     * per companyId for {@code findMaxRevision}.
     */
    private static final class FakeCompanyRevisionRepository implements CompanyRevisionRepository {
        final Map<Long, CompanyRevision> byId = new HashMap<>();
        final AtomicLong idGen = new AtomicLong(10_000L);

        @Override
        public Long findMaxRevision(Long companyId) {
            return byId.values().stream()
                    .filter(r -> r.companyId.equals(companyId))
                    .map(r -> r.revision)
                    .max(Long::compare)
                    .orElse(null);
        }

        @Override public <S extends CompanyRevision> S save(S entity) {
            if (entity.id == null) {
                entity.id = idGen.getAndIncrement();
            }
            byId.put(entity.id, entity);
            return entity;
        }

        @Override public Optional<CompanyRevision> findById(Long id) { return Optional.ofNullable(byId.get(id)); }

        // --- Unused methods ---
        @Override public <S extends CompanyRevision> Iterable<S> saveAll(Iterable<S> entities) { throw new UnsupportedOperationException(); }
        @Override public boolean existsById(Long aLong) { throw new UnsupportedOperationException(); }
        @Override public Iterable<CompanyRevision> findAll() { throw new UnsupportedOperationException(); }
        @Override public Iterable<CompanyRevision> findAllById(Iterable<Long> longs) { throw new UnsupportedOperationException(); }
        @Override public long count() { throw new UnsupportedOperationException(); }
        @Override public void deleteById(Long id) { throw new UnsupportedOperationException(); }
        @Override public void delete(CompanyRevision entity) { throw new UnsupportedOperationException(); }
        @Override public void deleteAllById(Iterable<? extends Long> longs) { throw new UnsupportedOperationException(); }
        @Override public void deleteAll(Iterable<? extends CompanyRevision> entities) { throw new UnsupportedOperationException(); }
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
            fakeRepo.byUniqueId.put(c.uniqueId, c);
            return c;
        }

        @Override public Company findByUniqueId(Long uniqueId) {
            return fakeRepo.findByUniqueId(uniqueId);
        }
    }

    /** Seed a company envelope (no satellite) with the given uniqueId. */
    private static Company envelope(long uniqueId) {
        Company c = new Company();
        c.id = uniqueId + 1000L;
        c.uniqueId = uniqueId;
        c.deleted = false;
        c.headRevisionId = null;
        return c;
    }

    /** Seed a head CompanyRevision (NAME, PARAMS) for the given envelope and wire it as head. */
    private static CompanyRevision seedHead(Company envelope, FakeCompanyRevisionRepository revRepo,
                                            String name, @Nullable String params, long revisionNumber) {
        CompanyRevision rev = new CompanyRevision();
        rev.companyId = envelope.id;
        rev.revision = revisionNumber;
        rev.name = name;
        rev.setParams(params);
        rev.deleted = false;
        rev.createdOn = System.currentTimeMillis();
        revRepo.save(rev);
        envelope.headRevisionId = rev.id;
        return rev;
    }

    private static VaultTxService newTx(FakeCompanyRepository repo,
                                        FakeCompanyRevisionRepository revRepo,
                                        FakeCompanyCache cache,
                                        ApplicationEventPublisher pub) {
        CompanyRevisionWriter writer = new CompanyRevisionWriter(repo, revRepo, cache);
        return new VaultTxService(repo, revRepo, cache, writer, pub);
    }

    @Test
    void saveVaultBlob_companyExists_persistsBlobAndEmitsEvent() {
        FakeCompanyRepository repo = new FakeCompanyRepository();
        FakeCompanyRevisionRepository revRepo = new FakeCompanyRevisionRepository();
        FakeCompanyCache cache = new FakeCompanyCache(repo);
        CapturingPublisher pub = new CapturingPublisher();

        long companyId = 42L;
        Company env = envelope(companyId);
        seedHead(env, revRepo, "co-42", null, 1L);
        repo.put(env);

        VaultTxService tx = newTx(repo, revRepo, cache, pub);
        CompanyParamsYaml.VaultEntries blob = new CompanyParamsYaml.VaultEntries("c2FsdA==", 200_000, "ZW5jcnlwdGVk");
        assertTrue(tx.saveVaultBlob(companyId, blob, "openai", VaultEntryChangedEvent.ACTION_PUT));

        // The new head revision must carry the new vault blob.
        CompanyRevision newHead = revRepo.findById(env.headRevisionId).orElseThrow();
        assertEquals(2L, newHead.revision, "writeNewRevision must produce revision=2 after seed revision=1");
        CompanyParamsYaml cpy = CompanyParamsYamlUtils.BASE_YAML_UTILS.to(newHead.getParams());
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
        FakeCompanyRevisionRepository revRepo = new FakeCompanyRevisionRepository();
        FakeCompanyCache cache = new FakeCompanyCache(repo);
        CapturingPublisher pub = new CapturingPublisher();

        VaultTxService tx = newTx(repo, revRepo, cache, pub);
        CompanyParamsYaml.VaultEntries blob = new CompanyParamsYaml.VaultEntries("c2FsdA==", 200_000, "ZW5jcnlwdGVk");
        assertFalse(tx.saveVaultBlob(99L, blob, "openai", VaultEntryChangedEvent.ACTION_PUT));

        assertNull(cache.saved);
        assertTrue(pub.events.isEmpty());
    }

    @Test
    void saveVaultBlob_preservesPreexistingAccessControl() {
        // Verify saving a vault blob does not clobber the existing access-control state from the head revision.
        FakeCompanyRepository repo = new FakeCompanyRepository();
        FakeCompanyRevisionRepository revRepo = new FakeCompanyRevisionRepository();
        FakeCompanyCache cache = new FakeCompanyCache(repo);
        CapturingPublisher pub = new CapturingPublisher();

        long companyId = 42L;
        Company env = envelope(companyId);
        CompanyParamsYaml seed = new CompanyParamsYaml();
        seed.ac = new CompanyParamsYaml.AccessControl("ops,devs");
        seed.createdOn = 1_700_000_000_000L;
        String seededParams = CompanyParamsYamlUtils.BASE_YAML_UTILS.toString(seed);
        seedHead(env, revRepo, "co-42", seededParams, 1L);
        repo.put(env);

        VaultTxService tx = newTx(repo, revRepo, cache, pub);
        CompanyParamsYaml.VaultEntries blob = new CompanyParamsYaml.VaultEntries("c2FsdA==", 200_000, "ZW5jcnlwdGVk");
        assertTrue(tx.saveVaultBlob(companyId, blob, "openai", VaultEntryChangedEvent.ACTION_PUT));

        CompanyRevision newHead = revRepo.findById(env.headRevisionId).orElseThrow();
        CompanyParamsYaml after = CompanyParamsYamlUtils.BASE_YAML_UTILS.to(newHead.getParams());
        assertNotNull(after.ac);
        assertEquals("ops,devs", after.ac.groups);
        assertNotNull(after.vault);
        assertEquals(1_700_000_000_000L, after.createdOn, "createdOn must be preserved");
    }

    @Test
    void loadVaultBlob_companyExists_withVault_returnsBlob() {
        FakeCompanyRepository repo = new FakeCompanyRepository();
        FakeCompanyRevisionRepository revRepo = new FakeCompanyRevisionRepository();
        FakeCompanyCache cache = new FakeCompanyCache(repo);
        CapturingPublisher pub = new CapturingPublisher();

        long companyId = 7L;
        Company env = envelope(companyId);
        CompanyParamsYaml seed = new CompanyParamsYaml();
        seed.vault = new CompanyParamsYaml.VaultEntries("c2FsdA==", 200_000, "ZW5jcnlwdGVk");
        String seededParams = CompanyParamsYamlUtils.BASE_YAML_UTILS.toString(seed);
        seedHead(env, revRepo, "co-7", seededParams, 1L);
        repo.put(env);

        VaultTxService tx = newTx(repo, revRepo, cache, pub);
        CompanyParamsYaml.VaultEntries blob = tx.loadVaultBlob(companyId);
        assertNotNull(blob);
        assertEquals("c2FsdA==", blob.salt);
        assertEquals(200_000, blob.iterations);
        assertEquals("ZW5jcnlwdGVk", blob.encryptedEntries);
    }

    @Test
    void loadVaultBlob_companyExists_withoutVault_returnsNull() {
        FakeCompanyRepository repo = new FakeCompanyRepository();
        FakeCompanyRevisionRepository revRepo = new FakeCompanyRevisionRepository();
        FakeCompanyCache cache = new FakeCompanyCache(repo);
        CapturingPublisher pub = new CapturingPublisher();

        long companyId = 7L;
        Company env = envelope(companyId);
        seedHead(env, revRepo, "co-7", null, 1L);
        repo.put(env);

        VaultTxService tx = newTx(repo, revRepo, cache, pub);
        assertNull(tx.loadVaultBlob(companyId));
    }

    @Test
    void loadVaultBlob_companyMissing_returnsNull() {
        FakeCompanyRepository repo = new FakeCompanyRepository();
        FakeCompanyRevisionRepository revRepo = new FakeCompanyRevisionRepository();
        FakeCompanyCache cache = new FakeCompanyCache(repo);
        CapturingPublisher pub = new CapturingPublisher();
        VaultTxService tx = newTx(repo, revRepo, cache, pub);

        assertNull(tx.loadVaultBlob(123L));
    }
}
