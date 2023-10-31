/*
 * Metaheuristic, Copyright (C) 2017-2023, Innovation platforms, LLC
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

package ai.metaheuristic.ai.dispatcher.rest.v1;

import ai.metaheuristic.ai.processor.net.HttpClientExecutor;
import org.apache.hc.client5.http.entity.mime.HttpMultipartMode;
import org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder;
import org.apache.hc.client5.http.fluent.Executor;
import org.apache.hc.client5.http.fluent.Request;
import org.apache.hc.client5.http.fluent.Response;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.util.Timeout;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * @author Sergio Lissner
 * Date: 10/28/2023
 * Time: 9:59 PM
 */

@Disabled("Those tests for manual testing")
public class StatusRestControllerTest {

    @Test
    public void test_sourceCode() throws Exception {
        final String s = queryRest("http://localhost:8080/rest/v1/dispatcher/status/source-code/7018");
        System.out.println(s);
    }

    @Test
    public void test_exec_context() throws Exception {
        final String s = queryRest("http://localhost:8080/rest/v1/dispatcher/status/exec-context/5753");
        System.out.println(s);
    }

    private static String queryRest(String uri) throws IOException {
        Request request = Request.get(uri);
        final Executor executor = HttpClientExecutor.getExecutor(uri, "q", "123");

        Response response = assertDoesNotThrow(()->executor.execute(request));

        String s = response.returnContent().asString();
        return s;
    }
}
