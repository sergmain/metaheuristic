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

import ai.metaheuristic.ai.data.DispatcherData;
import ai.metaheuristic.ai.yaml.processor_status.ProcessorStatusYaml;
import org.springframework.lang.Nullable;

/**
 * @author Serge
 * Date: 10/26/2021
 * Time: 1:08 AM
 */
public class QuotasUtils {

    @Nullable
    public static Integer getQuotaAmount(ProcessorStatusYaml.Quotas processorQuotas, @Nullable String tag) {
        if (processorQuotas.disabled) {
            return null;
        }
        if (processorQuotas.defaultValue==0) {
            throw new IllegalStateException("(processorQuotas.defaultValue==0)");
        }
        if (tag==null) {
            return processorQuotas.defaultValue;
        }
        return processorQuotas.values.stream().filter(o->o.tag.equals(tag)).findFirst().map(o->o.amount).orElse(processorQuotas.defaultValue);
    }

    public static boolean isEnough(ProcessorStatusYaml.Quotas processorQuotas, DispatcherData.TaskQuotas quotas, @Nullable Integer quota) {
        if (processorQuotas.disabled) {
            return true;
        }

        if (quota==null) {
            throw new IllegalStateException("(quota==null)");
        }

        return processorQuotas.limit>= (quota + quotas.initial + quotas.allocated.stream().mapToInt(o -> o.amount).sum());
    }
}
