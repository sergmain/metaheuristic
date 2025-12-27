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

package ai.metaheuristic.commons.yaml;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import javax.annotation.Nullable;
import org.yaml.snakeyaml.Yaml;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author Serge
 * Date: 9/9/2020
 * Time: 3:41 AM
 */
@Data
@Slf4j
public class YamlSchemeValidator<T> {

    public final List<Scheme> schemes;
    public final String filename;
    public final Function<String, T> exitFunction;
    public final String mainSeeMoreInfo;

    @AllArgsConstructor
    public static class Element {
        public final String name;
        public final boolean required;
        public final boolean deprecated;
        public final List<Element> elements;

        public Element(String name, boolean required, boolean deprecated, String[] names) {
            this.name = name;
            this.required = required;
            this.deprecated = deprecated;
            this.elements = Arrays.stream(names).map(n->new Element(n, true, false, List.of())).collect(Collectors.toList());
        }

        public Element(String name) {
            this.name = name;
            this.required = true;
            this.deprecated = false;
            this.elements = List.of();
        }

        public Element(String name, boolean required, boolean deprecated) {
            this.name = name;
            this.required = required;
            this.deprecated = deprecated;
            this.elements = List.of();
        }
    }

    @AllArgsConstructor
    public static class Scheme {
        public final List<Element> roots;
        public final int version;
        public final String seeMoreInfo;
        public boolean supported;
    }

    public YamlSchemeValidator(List<Scheme> schemes, String filename, Function<String, T> exitFunction, String mainSeeMoreInfo)  {
        this.schemes = schemes;
        this.filename = filename;
        this.exitFunction = exitFunction;
        this.mainSeeMoreInfo = mainSeeMoreInfo;
    }

    @SuppressWarnings({"rawtypes", "unchecked", "RedundantCast"})
    @Nullable
    public T validateStructureOfDispatcherYaml(String cfg) {
        Yaml yaml = YamlUtils.init(Map.class);
        Map m = (Map) YamlUtils.to(cfg, yaml);

        Object versionObj = m.get("version");
        int currVersion = 1;
        if ((versionObj instanceof String)) {
            currVersion = Integer.parseInt((String)versionObj);
        }
        else if ((versionObj instanceof Integer)) {
            currVersion = ((Integer)versionObj);
        }
        else if ((versionObj instanceof Number)) {
            currVersion = ((Number)versionObj).intValue();
        }
        else if (versionObj!=null) {
            final String es = "\nValue of version element must be string. "+filename+"\n" + mainSeeMoreInfo;
            log.error(es);
            return exitFunction.apply(es);
        }

        final int finalVersion = currVersion;
        Scheme scheme = schemes.stream().filter(o->o.version==finalVersion).findFirst().orElse(null);

        if (scheme==null) {
            final String es = "\nNot supported version of "+filename+". Must be one of "+schemes.stream().map(o->o.version).collect(Collectors.toList())+".\n" + mainSeeMoreInfo;
            log.error(es);
            return exitFunction.apply(es);
        }


        for (Element re : scheme.roots) {
            if (!m.containsKey(re.name) && re.required) {
                String es = "\n\n!!! Root element '"+re.name+"' wasn't found in "+filename+"\n"+scheme.seeMoreInfo;
                log.error(es);
                return exitFunction.apply(es);
            }
        }

        boolean isAnyError = false;
        List<String> unknowns = new ArrayList<>();

        for (Element re : scheme.roots) {

            Object rootObj = m.get(re.name);
            if (rootObj==null && !re.required) {
                continue;
            }
            if (!(rootObj instanceof List)) {
                final String es = "\nBroken content of "+filename+". Must be in .yaml format.\n" + scheme.seeMoreInfo;
                log.error(es);
                return exitFunction.apply(es);
            }

            AtomicBoolean isError = new AtomicBoolean();
            for (Object o : (List) rootObj) {
                if (!(o instanceof Map)) {
                    final String es = "\nBroken content of "+filename+". Must be in .yaml format.\n" + scheme.seeMoreInfo;
                    log.error(es);
                    return exitFunction.apply(es);
                }

                Map<String, Object> props = (Map)o;
                Set<String> presentedElements = new HashSet<>();
                for (Map.Entry<String, Object> entry : props.entrySet()) {
                    if (re.elements.stream().noneMatch(e->e.name.equals(entry.getKey()))) {
                        unknowns.add(entry.getKey());
                        log.error("\n"+filename+", unknown property: " + entry.getKey());
                        isError.set(true);
                    }
                    else {
                        presentedElements.add(entry.getKey());
                    }
                    if (re.elements.stream().anyMatch(e->e.name.equals(entry.getKey()) && e.deprecated )) {
                        log.error("\n\tproperty '" + entry.getKey()+"' is deprecated and has to be removed from "+filename+".");
                    }
                }
                re.elements.forEach(e -> {
                    if (!presentedElements.contains(e.name) && e.required) {
                        log.error("\n" + filename + ", property " + e.name + "  is required.");
                        isError.set(true);
                    }
                });
            }
            if (isError.get()) {
                if (!unknowns.isEmpty()) {
                    final String es = "\nUnknown elements " + unknowns + " were encountered in " + filename + ".\n" +
                            "Need to be fixed.\n" +
                            "Allowed elements are: " + re.elements.stream().map(o -> o.name).collect(Collectors.joining(", ")) + "\n" +
                            scheme.seeMoreInfo;
                    log.error(es);
                }
                isAnyError = true;
            }
        }

        if (isAnyError) {
            return exitFunction.apply("There is an error with " + filename);
        }
        return null;
    }
}
