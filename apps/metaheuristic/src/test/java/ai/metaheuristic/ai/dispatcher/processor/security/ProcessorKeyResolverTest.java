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

package ai.metaheuristic.ai.dispatcher.processor.security;

import ai.metaheuristic.ai.dispatcher.beans.Processor;
import ai.metaheuristic.ai.dispatcher.processor.ProcessorCache;
import ai.metaheuristic.ai.yaml.processor_status.ProcessorStatusYaml;
import ai.metaheuristic.ai.yaml.processor_status.ProcessorStatusYamlUtils;
import ai.metaheuristic.ai.processor.security.ProcessorKeyPair;
import ai.metaheuristic.commons.security.AsymmetricEncryptor;
import ai.metaheuristic.commons.security.CreateKeys;
import ai.metaheuristic.commons.security.SealedSecret;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;

import java.security.MessageDigest;
import java.security.PublicKey;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ProcessorKeyResolver}.
 *
 * <p>The resolver depends only on {@link ProcessorCache}; we mock it directly
 * without a Spring context. Pure unit test → multiple {@code @Test} methods
 * in one class.
 *
 * @author Sergio Lissner
 */
@Execution(CONCURRENT)
class ProcessorKeyResolverTest {

    @Test
    void test_publicKeyFor_returnsEmpty_whenProcessorUnknown() {
        ProcessorCache cache = mock(ProcessorCache.class);
        when(cache.findById(42L)).thenReturn(null);
        ProcessorKeyResolver resolver = new ProcessorKeyResolver(cache);

        assertTrue(resolver.publicKeyFor(42L).isEmpty());
    }

    @Test
    void test_publicKeyFor_returnsEmpty_whenSpkiMissing() {
        ProcessorCache cache = mock(ProcessorCache.class);
        Processor row = newProcessorWithSpki(null, null);
        when(cache.findById(42L)).thenReturn(row);
        ProcessorKeyResolver resolver = new ProcessorKeyResolver(cache);

        assertTrue(resolver.publicKeyFor(42L).isEmpty());
    }

    @Test
    void test_publicKeyFor_decodesSpki() throws Exception {
        CreateKeys ck = new CreateKeys(2048);
        byte[] spki = ck.getPublicKey().getEncoded();
        String spkiB64 = Base64.getEncoder().encodeToString(spki);
        String fp = sha256Hex(spki);

        ProcessorCache cache = mock(ProcessorCache.class);
        when(cache.findById(42L)).thenReturn(newProcessorWithSpki(spkiB64, fp));
        ProcessorKeyResolver resolver = new ProcessorKeyResolver(cache);

        PublicKey pk = resolver.publicKeyFor(42L).orElseThrow();
        assertArrayEquals(spki, pk.getEncoded());
    }

    @Test
    void test_publicKeyFor_evictsCache_whenFingerprintChanges() throws Exception {
        CreateKeys ck1 = new CreateKeys(2048);
        CreateKeys ck2 = new CreateKeys(2048);
        byte[] spki1 = ck1.getPublicKey().getEncoded();
        byte[] spki2 = ck2.getPublicKey().getEncoded();
        String spki1B64 = Base64.getEncoder().encodeToString(spki1);
        String spki2B64 = Base64.getEncoder().encodeToString(spki2);
        String fp1 = sha256Hex(spki1);
        String fp2 = sha256Hex(spki2);

        ProcessorCache cache = mock(ProcessorCache.class);
        ProcessorKeyResolver resolver = new ProcessorKeyResolver(cache);

        // First read — fp1
        when(cache.findById(42L)).thenReturn(newProcessorWithSpki(spki1B64, fp1));
        PublicKey first = resolver.publicKeyFor(42L).orElseThrow();
        assertArrayEquals(spki1, first.getEncoded());

        // Same fingerprint — cache hit; second call returns the same PublicKey instance.
        PublicKey firstAgain = resolver.publicKeyFor(42L).orElseThrow();
        assertSame(first, firstAgain);

        // Now fingerprint changes (Processor reboot, fresh keypair) — cache must be evicted.
        when(cache.findById(42L)).thenReturn(newProcessorWithSpki(spki2B64, fp2));
        PublicKey second = resolver.publicKeyFor(42L).orElseThrow();
        assertArrayEquals(spki2, second.getEncoded());
        assertNotSame(first, second);
    }

    @Test
    void test_publicKeyFor_returnsEmpty_whenSpkiInvalidBase64() {
        ProcessorCache cache = mock(ProcessorCache.class);
        when(cache.findById(42L)).thenReturn(newProcessorWithSpki("not-base64-!@#$", "abc"));
        ProcessorKeyResolver resolver = new ProcessorKeyResolver(cache);

        assertTrue(resolver.publicKeyFor(42L).isEmpty());
    }

    /**
     * End-to-end Stage 4 flow:
     *   1. ProcessorKeyPair generates RSA-2048 in memory (Processor side).
     *   2. SPKI is Base64-encoded + SHA-256 fingerprinted (the keep-alive populate logic).
     *   3. The same two fields land on the persisted ProcessorStatusYaml
     *      (the ProcessorTxService.processKeepAliveData copy, simulated here).
     *   4. ProcessorKeyResolver decodes the SPKI back into a JCA PublicKey.
     *   5. AsymmetricEncryptor.encrypt with the resolved PublicKey, decrypt with
     *      the in-memory PrivateKey — round-trip succeeds.
     */
    @Test
    void test_endToEnd_processorKeypair_throughResolver_throughEncryption() throws Exception {
        // 1. Processor side: in-memory keypair.
        ProcessorKeyPair kp = new ProcessorKeyPair();
        kp.init();
        byte[] spki = kp.getPublicKeySpki();

        // 2. Wire-side encoding (what ProcessorService.produceReportProcessorStatus does).
        String spkiB64 = Base64.getEncoder().encodeToString(spki);
        String fp = sha256Hex(spki);

        // 3. Dispatcher persists the two fields onto Processor.STATUS (what
        //    ProcessorTxService.processKeepAliveData does inside the
        //    if(processorStatusDifferent) block).
        ProcessorCache cache = mock(ProcessorCache.class);
        when(cache.findById(42L)).thenReturn(newProcessorWithSpki(spkiB64, fp));
        ProcessorKeyResolver resolver = new ProcessorKeyResolver(cache);

        // 4. Dispatcher resolves the PublicKey for the Processor.
        PublicKey resolved = resolver.publicKeyFor(42L).orElseThrow();
        assertArrayEquals(spki, resolved.getEncoded());

        // 5. Encrypt under the resolved key, decrypt with the Processor's in-memory private key.
        byte[] plaintext = "stage-4 end-to-end secret".getBytes();
        SealedSecret sealed = AsymmetricEncryptor.encrypt(plaintext, resolved);
        byte[] recovered = AsymmetricEncryptor.decrypt(sealed, kp.getPrivateKey());
        assertArrayEquals(plaintext, recovered);
    }

    // ---- helpers -----------------------------------------------------------

    private static Processor newProcessorWithSpki(String spkiB64, String fp) {
        Processor p = new Processor();
        ProcessorStatusYaml psy = new ProcessorStatusYaml();
        psy.sessionId = "sess";
        psy.sessionCreatedOn = 0L;
        psy.ip = "0.0.0.0";
        psy.host = "h";
        psy.schedule = "";
        psy.logDownloadable = false;
        psy.taskParamsVersion = 0;
        psy.currDir = ".";
        psy.publicKeySpki = spkiB64;
        psy.keyFingerprint = fp;
        p.updateParams(psy);  // serialize psy into the STATUS column
        return p;
    }

    private static String sha256Hex(byte[] data) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(md.digest(data));
    }
}
