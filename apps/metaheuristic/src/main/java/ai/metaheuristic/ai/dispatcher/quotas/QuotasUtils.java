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

package ai.metaheuristic.ai.dispatcher.quotas;

import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.data.DispatcherData;
import ai.metaheuristic.ai.dispatcher.data.QuotasData;
import ai.metaheuristic.ai.yaml.processor_status.ProcessorStatusYaml;
import org.springframework.lang.Nullable;

/**
 * @author Serge
 * Date: 10/26/2021
 * Time: 1:08 AM
 */
public class QuotasUtils {

    public static QuotasData.ActualQuota getQuotaAmount(ProcessorStatusYaml.Quotas processorQuotas, @Nullable String tag) {
        if (processorQuotas.disabled) {
            return new QuotasData.ActualQuota(Enums.QuotaAllocation.disabled, 0);
        }
        if (tag==null) {
            return new QuotasData.ActualQuota(Enums.QuotaAllocation.present, processorQuotas.defaultValue);
        }
        return new QuotasData.ActualQuota(
                Enums.QuotaAllocation.present,
                processorQuotas.values.stream().filter(o->!o.disabled && o.tag.equals(tag)).findFirst().map(o->o.amount).orElse(processorQuotas.defaultValue));
    }

    /**
     *
     * @param processorQuotas
     * @param quotas
     * @param quota calculated quota for concrete tag
     * @return
     */
    public static boolean isEnough(ProcessorStatusYaml.Quotas processorQuotas, DispatcherData.TaskQuotas quotas, QuotasData.ActualQuota quota) {
        if (processorQuotas.disabled) {
            return true;
        }

        if (quota.quotaAllocation== Enums.QuotaAllocation.disabled) {
            throw new IllegalStateException("(quota.quotaAllocation== Enums.QuotaAllocation.disabled)");
        }

        return processorQuotas.limit >= (quota.amount + quotas.initial + quotas.allocated.stream().mapToInt(o -> o.amount).sum());
    }
}
