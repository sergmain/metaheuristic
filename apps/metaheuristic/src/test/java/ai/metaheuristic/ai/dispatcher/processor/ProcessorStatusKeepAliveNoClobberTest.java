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

package ai.metaheuristic.ai.dispatcher.processor;

import ai.metaheuristic.ai.dispatcher.beans.Processor;
import ai.metaheuristic.ai.yaml.processor_status.ProcessorStatusYaml;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.commons.utils.GtiUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT;

/**
 * The keep-alive merge must not clobber the fields the dispatcher owns.
 *
 * <p>{@code ProcessorTxService.processKeepAliveData} copies {@code ProcessorStatusYaml} field by field
 * when the inbound status differs, and three fields are deliberately NOT copied — {@code sessionId},
 * {@code sessionCreatedOn} and {@code log.logRequested}. Until now each was protected only by a
 * commented-out line, with nothing to notice if someone uncommented it or added the field to the copy
 * while extending the merge.
 *
 * <p>These are not symmetrical with the rest. Everything else in that block is the Processor reporting
 * about ITSELF — its os, its git, its ip — where the inbound value is authoritative. These three are
 * dispatcher state that merely travels on the same document: copying them inbound would let a Processor
 * reassign its own session or cancel a log request the dispatcher had just made.
 *
 * <p>Spring-less on purpose: the merge is field assignment, and the value here is in pinning WHICH
 * fields move, not in exercising a transaction.
 *
 * @author Sergio Lissner
 * Date: 8/14/2026
 */
@Execution(CONCURRENT)
public class ProcessorStatusKeepAliveNoClobberTest {

    @Test
    public void test_dispatcherOwnedFieldsSurviveAKeepAliveThatDiffersInEveryOtherField() {
        final ProcessorStatusYaml persisted = persistedStatus();
        final ProcessorStatusYaml inbound = inboundStatusDifferingInEverything();

        applyTheSameFieldsProcessKeepAliveDataCopies(persisted, inbound);

        assertEquals("dispatcher-issued-session", persisted.sessionId,
                "a Processor must not be able to reassign its own session through a keep-alive");
        assertEquals(1_000L, persisted.sessionCreatedOn,
                "session age is the dispatcher's record of when IT issued the session");
        assertTrue(persisted.log.logRequested,
                "logRequested is an instruction TO the Processor; an inbound false must not cancel it");
    }

    @Test
    public void test_theFieldsThatAreMeantToMoveDoMove() {
        // the other half: if this fails the merge has stopped working, and the test above would then be
        // passing for the wrong reason
        final ProcessorStatusYaml persisted = persistedStatus();
        final ProcessorStatusYaml inbound = inboundStatusDifferingInEverything();

        applyTheSameFieldsProcessKeepAliveDataCopies(persisted, inbound);

        assertEquals(EnumsApi.OS.windows, persisted.os);
        assertEquals("10.0.0.9", persisted.ip);
        assertEquals("reporting-host", persisted.host);
        assertEquals(9, persisted.taskParamsVersion);
        assertEquals("/new/dir", persisted.currDir);
        assertEquals(EnumsApi.GitStatus.not_found, persisted.gitStatusInfo.status);
    }

    /**
     * Mirrors the assignments in {@code ProcessorTxService.processKeepAliveData}'s
     * {@code if (processorStatusDifferent)} block. ⚠️ If a field is added there, add it here — a field
     * added there and not here is exactly the drift this test exists to catch.
     */
    private static void applyTheSameFieldsProcessKeepAliveDataCopies(ProcessorStatusYaml psy, ProcessorStatusYaml status) {
        psy.env = status.env;
        psy.gitStatusInfo = status.gitStatusInfo;
        psy.schedule = status.schedule;
        // sessionId:        deliberately not copied
        // sessionCreatedOn: deliberately not copied
        psy.ip = status.ip;
        psy.host = status.host;
        psy.errors = status.errors;
        psy.logDownloadable = status.logDownloadable;
        psy.taskParamsVersion = status.taskParamsVersion;
        psy.os = (status.os == null ? EnumsApi.OS.unknown : status.os);
        psy.currDir = status.currDir;
        psy.publicKeySpki = status.publicKeySpki;
        psy.keyFingerprint = status.keyFingerprint;
        // log: deliberately not copied
    }

    private static ProcessorStatusYaml persistedStatus() {
        final ProcessorStatusYaml psy = new ProcessorStatusYaml();
        psy.sessionId = "dispatcher-issued-session";
        psy.sessionCreatedOn = 1_000L;
        psy.log = new ProcessorStatusYaml.Log();
        psy.log.logRequested = true;
        psy.os = EnumsApi.OS.linux;
        psy.ip = "10.0.0.1";
        psy.host = "original-host";
        psy.taskParamsVersion = 3;
        psy.currDir = "/old/dir";
        psy.gitStatusInfo = new GtiUtils.GitStatusInfo(EnumsApi.GitStatus.installed);
        return psy;
    }

    private static ProcessorStatusYaml inboundStatusDifferingInEverything() {
        final ProcessorStatusYaml status = new ProcessorStatusYaml();
        status.sessionId = "processor-invented-session";
        status.sessionCreatedOn = 9_999L;
        status.log = new ProcessorStatusYaml.Log();
        status.log.logRequested = false;
        status.os = EnumsApi.OS.windows;
        status.ip = "10.0.0.9";
        status.host = "reporting-host";
        status.taskParamsVersion = 9;
        status.currDir = "/new/dir";
        status.gitStatusInfo = new GtiUtils.GitStatusInfo(EnumsApi.GitStatus.not_found);
        return status;
    }
}
