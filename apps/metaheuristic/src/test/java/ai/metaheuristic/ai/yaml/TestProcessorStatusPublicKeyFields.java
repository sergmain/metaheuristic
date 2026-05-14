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

package ai.metaheuristic.ai.yaml;

import ai.metaheuristic.ai.yaml.communication.keep_alive.KeepAliveRequestParamYaml;
import ai.metaheuristic.ai.yaml.communication.keep_alive.KeepAliveRequestParamYamlUtils;
import ai.metaheuristic.ai.yaml.processor_status.ProcessorStatusYaml;
import ai.metaheuristic.ai.yaml.processor_status.ProcessorStatusYamlUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT;

/**
 * Stage 4: publicKeySpki + keyFingerprint round-trip through both
 * {@code ProcessorStatusYaml} (persisted) and
 * {@code KeepAliveRequestParamYaml.ProcessorStatus} (wire).
 *
 * <p>Legacy YAML (without the new fields) must still load — the @Nullable
 * exception in the multi-versioning mechanic requires this.
 *
 * @author Sergio Lissner
 */
@Execution(CONCURRENT)
public class TestProcessorStatusPublicKeyFields {

    private static final String SAMPLE_SPKI_B64 = "QkFTRTY0LVNQS0k=";
    private static final String SAMPLE_FP = "deadbeef" + "00".repeat(28);

    @Test
    public void test_processorStatusYaml_roundTripsPublicKeyFields() {
        ProcessorStatusYaml in = new ProcessorStatusYaml();
        in.sessionId = "abc";
        in.sessionCreatedOn = 0L;
        in.ip = "1.2.3.4";
        in.host = "h";
        in.schedule = "";
        in.logDownloadable = false;
        in.taskParamsVersion = 0;
        in.currDir = ".";
        in.publicKeySpki = SAMPLE_SPKI_B64;
        in.keyFingerprint = SAMPLE_FP;

        String yaml = ProcessorStatusYamlUtils.BASE_YAML_UTILS.toString(in);
        ProcessorStatusYaml out = ProcessorStatusYamlUtils.BASE_YAML_UTILS.to(yaml);

        assertEquals(in.publicKeySpki, out.publicKeySpki);
        assertEquals(in.keyFingerprint, out.keyFingerprint);
    }

    @Test
    public void test_processorStatusYaml_loadsLegacyYamlWithoutPublicKeyFields() {
        // Minimal YAML body at version 3 with no publicKey* fields present.
        String legacyYaml = """
            version: 3
            sessionId: "abc"
            sessionCreatedOn: 0
            ip: "1.2.3.4"
            host: "h"
            schedule: ""
            logDownloadable: false
            taskParamsVersion: 0
            currDir: "."
            """;
        ProcessorStatusYaml out = ProcessorStatusYamlUtils.BASE_YAML_UTILS.to(legacyYaml);
        assertNull(out.publicKeySpki);
        assertNull(out.keyFingerprint);
    }

    @Test
    public void test_keepAliveRequestParamYaml_processorStatus_roundTripsPublicKeyFields() {
        KeepAliveRequestParamYaml karpy = new KeepAliveRequestParamYaml();
        karpy.processor.status = new KeepAliveRequestParamYaml.ProcessorStatus();
        karpy.processor.status.publicKeySpki = SAMPLE_SPKI_B64;
        karpy.processor.status.keyFingerprint = SAMPLE_FP;
        karpy.processor.status.schedule = "";
        karpy.processor.status.ip = "1.2.3.4";
        karpy.processor.status.host = "h";
        karpy.processor.status.currDir = ".";
        // env may stay null; the V3 upgrade path skips copy if env is null.

        String yaml = KeepAliveRequestParamYamlUtils.BASE_YAML_UTILS.toString(karpy);
        KeepAliveRequestParamYaml out = KeepAliveRequestParamYamlUtils.BASE_YAML_UTILS.to(yaml);

        assertNotNull(out.processor.status);
        assertEquals(SAMPLE_SPKI_B64, out.processor.status.publicKeySpki);
        assertEquals(SAMPLE_FP, out.processor.status.keyFingerprint);
    }
}
