/*
 *    Copyright 2023, Sergio Lissner, Innovation platforms, LLC
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 *
 */

package ai.metaheuristic.mhbp.provider;

import ai.metaheuristic.ai.mhbp.provider.ProviderApiSchemeService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Sergio Lissner
 * Date: 4/19/2023
 * Time: 2:21 AM
 */
public class ProviderApiSchemeServiceTest {

    @Test
    public void test_getEnvParamName() {
        assertEquals("A", ProviderApiSchemeService.getEnvParamName("A"));
        assertEquals("A", ProviderApiSchemeService.getEnvParamName("%A"));
        assertEquals("A", ProviderApiSchemeService.getEnvParamName("%A%"));
        assertEquals("A", ProviderApiSchemeService.getEnvParamName("$A"));
        assertEquals("A", ProviderApiSchemeService.getEnvParamName("$A$"));
    }
}
