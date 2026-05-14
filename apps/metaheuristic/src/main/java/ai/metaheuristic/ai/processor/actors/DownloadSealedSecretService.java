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
package ai.metaheuristic.ai.processor.actors;

import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.dispatcher.data.SealedSecretData;
import ai.metaheuristic.ai.processor.CurrentExecState;
import ai.metaheuristic.ai.processor.ProcessorTaskService;
import ai.metaheuristic.ai.processor.net.HttpClientExecutor;
import ai.metaheuristic.ai.processor.secret.SealedSecretCache;
import ai.metaheuristic.ai.processor.tasks.DownloadSealedSecretTask;
import ai.metaheuristic.ai.utils.RestUtils;
import ai.metaheuristic.ai.yaml.processor_task.ProcessorCoreTask;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.commons.security.SealedSecret;
import ai.metaheuristic.commons.security.SealedSecretCodec;
import ai.metaheuristic.commons.utils.JsonUtils;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.HttpHostConnectException;
import org.apache.hc.client5.http.HttpResponseException;
import org.apache.hc.client5.http.fluent.Form;
import org.apache.hc.client5.http.fluent.Request;
import org.apache.hc.client5.http.fluent.Response;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.net.URIBuilder;
import org.apache.hc.core5.util.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Per-task sealed-secret fetch actor. Modeled on
 * {@link GetDispatcherContextInfoService} (single-shot POST/form pattern).
 *
 * <p>For each queued {@link DownloadSealedSecretTask}, POSTs to
 * {@code /rest/v1/processor/sealed-secret} with form params
 * {@code processorId}, {@code companyId}, {@code keyCode} and populates
 * {@link SealedSecretCache} on success.
 *
 * <p>Response routing:
 * <ul>
 *   <li>200 — parse {@code FetchResponse}, decode Base64, populate cache.</li>
 *   <li>401 — log warn, return (retry next cycle).</li>
 *   <li>404 — log info, return (Stage 4 not yet satisfied for this Processor;
 *       transient).</li>
 *   <li>410 — log warn, {@code markAsFinishedWithError} (Vault has no entry
 *       for {@code (companyId, keyCode)}, permanent).</li>
 *   <li>502 / other 5xx — log warn/error, return (transient).</li>
 *   <li>SocketTimeout / Connect / IO — log error, return.</li>
 * </ul>
 *
 * <p>Error-code prefix {@code 812.} is unique to this class.
 *
 * @author Sergio Lissner
 */
@Service
@Slf4j
@Profile("processor")
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class DownloadSealedSecretService
        extends AbstractTaskQueue<DownloadSealedSecretTask>
        implements QueueProcessor {

    private final Globals globals;
    private final ProcessorTaskService processorTaskService;
    private final CurrentExecState currentExecState;
    private final SealedSecretCache sealedSecretCache;

    @Override
    public void process() {
        if (globals.testing) {
            return;
        }
        if (!globals.processor.enabled) {
            return;
        }

        DownloadSealedSecretTask task;
        while ((task = poll()) != null) {
            processTask(task);
        }
    }

    private void processTask(DownloadSealedSecretTask task) {
        // Pre-flight (same shape as DownloadVariableService).
        ProcessorCoreTask processorTask = processorTaskService.findByIdForCore(task.core, task.taskId);
        if (processorTask == null) {
            log.info("812.005 Task #{} wasn't found, skip sealed-secret fetch", task.taskId);
            return;
        }
        if (processorTask.finishedOn != null) {
            log.info("812.007 Task #{} was already finished, skip sealed-secret fetch", task.taskId);
            return;
        }
        EnumsApi.ExecContextState state = currentExecState.getState(task.core.dispatcherUrl, processorTask.execContextId);
        if (state != EnumsApi.ExecContextState.STARTED) {
            log.info("812.009 ExecContext #{} is stopped, delete task #{}", processorTask.execContextId, task.taskId);
            processorTaskService.delete(task.core, task.taskId);
            return;
        }

        // Skip-if-already-have: a previous task's fetch (or push refresh) may
        // have populated the cache between enqueue and this poll.
        if (sealedSecretCache.get(task.companyId, task.keyCode) != null) {
            log.debug("812.011 Cache already populated for companyId={}, keyCode={}; skip fetch",
                    task.companyId, task.keyCode);
            return;
        }

        final String uri = task.dispatcher.url + "/rest/v1/processor/sealed-secret";
        try {
            final URIBuilder builder = new URIBuilder(uri).setCharset(StandardCharsets.UTF_8);
            final Request request = Request.post(builder.build())
                    .bodyForm(Form.form()
                            .add("processorId", Long.toString(task.processorId))
                            .add("companyId", Long.toString(task.companyId))
                            .add("keyCode", task.keyCode)
                            .build(), StandardCharsets.UTF_8)
                    .connectTimeout(Timeout.ofSeconds(5));

            RestUtils.addHeaders(request);

            Response response = HttpClientExecutor.getExecutor(
                    task.core.dispatcherUrl.url, task.dispatcher.restUsername, task.dispatcher.restPassword).execute(request);

            final HttpResponse httpResponse = response.returnResponse();
            if (!(httpResponse instanceof ClassicHttpResponse classicHttpResponse)) {
                throw new IllegalStateException("(!(httpResponse instanceof ClassicHttpResponse classicHttpResponse))");
            }
            final int statusCode = classicHttpResponse.getCode();

            if (statusCode == HttpServletResponse.SC_OK) {
                handleOk(task, classicHttpResponse);
                return;
            }
            if (statusCode == HttpServletResponse.SC_UNAUTHORIZED) {
                log.warn("812.020 Unauthorized fetching sealed secret for companyId={}, keyCode={}",
                        task.companyId, task.keyCode);
                return;
            }
            if (statusCode == HttpServletResponse.SC_NOT_FOUND) {
                log.info("812.030 Processor not yet enrolled (no publicKeySpki) for sealed-secret fetch, companyId={}, keyCode={}; retry next cycle",
                        task.companyId, task.keyCode);
                return;
            }
            if (statusCode == HttpServletResponse.SC_GONE) {
                String es = String.format(
                        "812.040 Vault has no entry for companyId=%d, keyCode=%s. Task #%d is finished with error.",
                        task.companyId, task.keyCode, task.taskId);
                log.warn(es);
                processorTaskService.markAsFinishedWithError(task.core, task.taskId, es);
                return;
            }
            if (statusCode == HttpServletResponse.SC_BAD_GATEWAY) {
                log.warn("812.050 BAD_GATEWAY fetching sealed secret for companyId={}, keyCode={}; retry next cycle",
                        task.companyId, task.keyCode);
                return;
            }
            log.error("812.060 Unexpected http status code: {} while fetching sealed secret for companyId={}, keyCode={}",
                    statusCode, task.companyId, task.keyCode);
        } catch (HttpResponseException e) {
            // HttpClient fluent throws HttpResponseException for >= 300 in some configurations.
            final int sc = e.getStatusCode();
            if (sc == HttpServletResponse.SC_GONE) {
                String es = String.format(
                        "812.041 Vault has no entry for companyId=%d, keyCode=%s. Task #%d is finished with error.",
                        task.companyId, task.keyCode, task.taskId);
                log.warn(es);
                processorTaskService.markAsFinishedWithError(task.core, task.taskId, es);
                return;
            }
            if (sc == HttpServletResponse.SC_NOT_FOUND) {
                log.info("812.031 Processor not yet enrolled (HttpResponseException), companyId={}, keyCode={}",
                        task.companyId, task.keyCode);
                return;
            }
            if (sc == HttpServletResponse.SC_BAD_GATEWAY) {
                log.warn("812.051 BAD_GATEWAY (HttpResponseException) fetching sealed secret, companyId={}, keyCode={}",
                        task.companyId, task.keyCode);
                return;
            }
            log.error("812.065 HttpResponseException status={}, companyId={}, keyCode={}",
                    sc, task.companyId, task.keyCode, e);
        } catch (HttpHostConnectException e) {
            log.error("812.085 HttpHostConnectException, uri: {}, {}", uri, e.getMessage());
        } catch (SocketTimeoutException e) {
            log.error("812.070 SocketTimeoutException, uri: {}, {}", uri, e.getMessage());
        } catch (ConnectException e) {
            log.error("812.080 ConnectException, uri: {}, {}", uri, e.getMessage());
        } catch (IOException e) {
            log.error("812.090 IOException, uri: {}", uri, e);
        } catch (Throwable th) {
            log.error("812.099 Throwable, uri: " + uri, th);
        }
    }

    private void handleOk(DownloadSealedSecretTask task, ClassicHttpResponse classicHttpResponse) throws IOException {
        final String json;
        try (var is = classicHttpResponse.getEntity().getContent()) {
            json = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
        final SealedSecretData.FetchResponse resp =
                JsonUtils.getMapper().readValue(json, SealedSecretData.FetchResponse.class);
        if (resp.sealed() == null || resp.sealed().isBlank()) {
            log.error("812.100 200 response with empty 'sealed' field for companyId={}, keyCode={}",
                    task.companyId, task.keyCode);
            return;
        }
        final byte[] sealedBytes = Base64.getDecoder().decode(resp.sealed());
        final SealedSecret sealed = SealedSecretCodec.fromBytes(sealedBytes);
        sealedSecretCache.put(task.companyId, task.keyCode, sealed, resp.fingerprint(), resp.notAfter());
        log.info("812.110 Cached sealed secret for companyId={}, keyCode={}, notAfter={}",
                task.companyId, task.keyCode, resp.notAfter());
    }
}
