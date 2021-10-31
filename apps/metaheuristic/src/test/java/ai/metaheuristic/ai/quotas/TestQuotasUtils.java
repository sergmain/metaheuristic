/*
 * Metaheuristic, Copyright (C) 2017-2021, Innovation platforms, LLC
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

package ai.metaheuristic.ai.quotas;

import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.dispatcher.data.QuotasData;
import ai.metaheuristic.ai.dispatcher.quotas.QuotasUtils;
import org.junit.jupiter.api.Test;

import java.util.List;

import static ai.metaheuristic.ai.data.DispatcherData.AllocatedQuotas;
import static ai.metaheuristic.ai.data.DispatcherData.TaskQuotas;
import static ai.metaheuristic.ai.yaml.processor_status.ProcessorStatusYaml.Quota;
import static ai.metaheuristic.ai.yaml.processor_status.ProcessorStatusYaml.Quotas;
import static org.junit.jupiter.api.Assertions.*;


/**
 * @author Serge
 * Date: 10/26/2021
 * Time: 1:56 AM
 */
@SuppressWarnings("SimplifiableAssertion")
public class TestQuotasUtils {

    private static Quotas get(boolean disabled) {
        return get(disabled, 0, 0, List.of());
    }
    private static Quotas get(boolean disabled, int limit, int defaultValue) {
        return get(disabled, limit, defaultValue, List.of());
    }

    private static Quotas get(boolean disabled, int limit, int defaultValue, List<Quota> values) {
        Quotas q = new Quotas();
        q.limit = limit;
        q.defaultValue = defaultValue;
        q.disabled = disabled;
        q.values.addAll(values);
        return q;
    }

    private static TaskQuotas get(int initial) {
        return get(initial, List.of());
    }

    private static TaskQuotas get(int initial, List<AllocatedQuotas> allocated) {
        TaskQuotas q = new TaskQuotas(initial);
        q.allocated.addAll(allocated);
        return q;
    }

    public static QuotasData.ActualQuota getAQ(int amount) {
        return new QuotasData.ActualQuota(Enums.QuotaAllocation.present, amount);
    }

    @Test
    public void test_getQuotaAmount() {

//        public static Integer getQuotaAmount(ProcessorStatusYaml.Quotas processorQuotas, @Nullable String tag) {
        assertEquals(Enums.QuotaAllocation.disabled, QuotasUtils.getQuotaAmount(get(true), null).quotaAllocation);

        QuotasData.ActualQuota quotaAmount = QuotasUtils.getQuotaAmount(get(false, 100, 42), null);
        assertEquals(Enums.QuotaAllocation.present, quotaAmount.quotaAllocation);
        assertEquals(42, quotaAmount.amount);

        quotaAmount = QuotasUtils.getQuotaAmount(get(false, 100, 42, List.of(new Quota("a1", 15), new Quota("a2", 25))), "a1");
        assertEquals(Enums.QuotaAllocation.present, quotaAmount.quotaAllocation);
        assertEquals(15, quotaAmount.amount);

        quotaAmount = QuotasUtils.getQuotaAmount(get(false, 100, 42, List.of(new Quota("a1", 15), new Quota("a2", 25))), "a2");
        assertEquals(Enums.QuotaAllocation.present, quotaAmount.quotaAllocation);
        assertEquals(25, quotaAmount.amount);

        quotaAmount = QuotasUtils.getQuotaAmount(get(false, 100, 42, List.of(new Quota("a1", 15), new Quota("a2", 25))), "a3");
        assertEquals(Enums.QuotaAllocation.present, quotaAmount.quotaAllocation);
        assertEquals(42, quotaAmount.amount);

    }

    @Test
    public void test_isEnough() {
//        public static boolean isEnough(ProcessorStatusYaml.Quotas processorQuotas, DispatcherData.TaskQuotas quotas, @Nullable Integer quota) {
        assertTrue(QuotasUtils.isEnough(get(true), get(10), new QuotasData.ActualQuota(Enums.QuotaAllocation.disabled, 0)));

        assertThrows(IllegalStateException.class, ()->QuotasUtils.isEnough(get(false, 100, 42), get(10), new QuotasData.ActualQuota(Enums.QuotaAllocation.disabled, 0)));

        assertTrue(QuotasUtils.isEnough(get(false, 100, 42), get(10), getAQ(10)));
        assertFalse(QuotasUtils.isEnough(get(false, 100, 42), get(10), getAQ(100)));

        assertTrue(QuotasUtils.isEnough(get(false, 100, 42), get(0), getAQ(10)));
        assertFalse(QuotasUtils.isEnough(get(false, 100, 42), get(0), getAQ(110)));

        assertTrue(QuotasUtils.isEnough(
                get(false, 100, 42),
                get(10, List.of(new AllocatedQuotas(1L, "a", 5), new AllocatedQuotas(2L, "b", 55))),
                getAQ(10)));

        assertFalse(QuotasUtils.isEnough(
                get(false, 100, 42),
                get(10, List.of(new AllocatedQuotas(1L, "a", 5), new AllocatedQuotas(2L, "b", 55))),
                getAQ(100)));

        assertFalse(QuotasUtils.isEnough(
                get(false, 2, 1),
                get(0, List.of(new AllocatedQuotas(1L, "a", 1), new AllocatedQuotas(2L, "b", 1))),
                getAQ(1)));

    }
}
