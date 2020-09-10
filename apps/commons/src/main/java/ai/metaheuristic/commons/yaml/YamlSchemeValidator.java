/*
 * Metaheuristic, Copyright (C) 2017-2020  Serge Maslyukov
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

package ai.metaheuristic.commons.yaml;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;
import org.yaml.snakeyaml.Yaml;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * @author Serge
 * Date: 9/9/2020
 * Time: 3:41 AM
 */
@Data
@Slf4j
public class YamlSchemeValidator<T> {

    public final String rootElement;
    public final List<String> possibleElements2dnLevel;
    public final List<String> deprecatedElements;
    public final String seeMoreInfo;
    public final List<String> versions;
    public String filename;
    public Function<String, T> exitFunction;

    public YamlSchemeValidator(
            String rootElement, List<String> possibleElements2dnLevel,
            List<String> deprecatedElements, String seeMoreInfo, List<String> versions, String filename,
            Function<String, T> exitFunction)  {
        this.rootElement = rootElement;
        this.possibleElements2dnLevel = possibleElements2dnLevel;
        this.deprecatedElements = deprecatedElements;
        this.seeMoreInfo = seeMoreInfo;
        this.versions = versions;
        this.filename = filename;
        this.exitFunction = exitFunction;
    }

    @SuppressWarnings({"rawtypes", "unchecked", "RedundantCast"})
    @Nullable
    public T validateStructureOfDispatcherYaml(String cfg) {
        Yaml yaml = YamlUtils.init(Map.class);
        Map m = (Map) YamlUtils.to(cfg, yaml);

        if (!m.containsKey(rootElement)) {
            String es = "\n\n!!! Root element '"+rootElement+"' wasn't found in "+filename+"\n"+seeMoreInfo;
            log.error(es);
            return exitFunction.apply(es);
        }

        Object versionObj = m.get("version");
        if ((versionObj instanceof String) || (versionObj instanceof Integer)) {
            if (!versions.contains(versionObj.toString())) {
                final String es = "\nNot supported version of "+filename+". Must be one of "+versions+".\n" + seeMoreInfo;
                log.error(es);
                return exitFunction.apply(es);
            }
        }
        else if (versionObj!=null) {
            final String es = "\nValue of version element must be string. "+filename+"\n" + seeMoreInfo;
            log.error(es);
            return exitFunction.apply(es);
        }

        Object rootObj = m.get(rootElement);
        if (!(rootObj instanceof List)) {
            final String es = "\nBroken content of "+filename+". Must be in .yaml format.\n" + seeMoreInfo;
            log.error(es);
            return exitFunction.apply(es);
        }

        boolean isError = false;
        for (Object o : (List) rootObj) {
            if (!(o instanceof Map)) {
                final String es = "\nBroken content of "+filename+". Must be in .yaml format.\n" + seeMoreInfo;
                log.error(es);
                return exitFunction.apply(es);
            }

            Map<String, Object> props = (Map)o;
            for (Map.Entry<String, Object> entry : props.entrySet()) {
                if (!possibleElements2dnLevel.contains(entry.getKey())) {
                    log.error("\n"+filename+", unknown property: " + entry.getKey());
                    isError=true;
                }
                if (deprecatedElements.contains(entry.getKey())) {
                    log.error("\n\tproperty '" + entry.getKey()+"' is deprecated and has to be removed from "+filename+".");
                }
            }
        }

        if (isError) {
            final String es = "\nUnknown elements was encountered in " + filename + ".\n" +
                    "Need to be fixed.\n" +
                    "Allowed elements are: " + possibleElements2dnLevel + "\n" +
                    seeMoreInfo;
            log.error(es);
            return exitFunction.apply(es);
        }
        return null;
    }
}