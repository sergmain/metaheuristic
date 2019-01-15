/*
 AiAi, Copyright (C) 2017 - 2018, Serge Maslyukov

 This program is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program.  If not, see <https://www.gnu.org/licenses/>.

 */
package aiai.ai.launchpad.experiment;

import aiai.ai.Consts;
import aiai.ai.launchpad.beans.ExperimentSnippet;
import aiai.ai.yaml.hyper_params.HyperParams;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.matcher.StringMatcherFactory;

import java.util.*;

public class ExperimentUtils {

    private static final String RANGE = "range";
    private static final NumberOfVariants ZERO_VARIANT = new NumberOfVariants(true, null, 0);

    public static void sortExperimentSnippets(List<ExperimentSnippet> experimentSnippets) {
        experimentSnippets.sort((o1, o2) -> {
                    if (o1.getType().equals(o2.getType())) return 0;
                    return "fit".equals(o1.getType().toLowerCase()) ? -1 : 1;
                }
        );
    }

    @Data
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class NumberOfVariants {
        public boolean status;
        public String error;
        public int count;
        public final List<String> values = new ArrayList<>();

        public static NumberOfVariants instanceOf(boolean status, String error, int count) {
            return new NumberOfVariants(status, error, count);
        }

        public static NumberOfVariants instanceOf(boolean status, String error, int count,  final List<String> values ) {
            NumberOfVariants instance =  new NumberOfVariants(status, error, count);
            instance.values.addAll(values);
            return instance;
        }

        public static NumberOfVariants instanceOf(boolean status, String error, int count,  final String[] values ) {
            NumberOfVariants instance =  new NumberOfVariants(status, error, count);
            instance.values.addAll(Arrays.asList(values));
            return instance;
        }

        public static NumberOfVariants instanceOf(boolean status, String error, int count,  final String value ) {
            NumberOfVariants instance =  new NumberOfVariants(status, error, count);
            instance.values.add(value);
            return instance;
        }
    }

    public static List<HyperParams> getAllHyperParams(Map<String, String> experimentMetadatas) {
        if (experimentMetadatas==null || experimentMetadatas.isEmpty()) {
            return new ArrayList<>();
        }

        List<HyperParams> allHyperParams = new ArrayList<>();

        List<Map.Entry<String, String>> entries = new ArrayList<>(experimentMetadatas.entrySet());

        for (Map.Entry<String, String> entry : entries) {
            NumberOfVariants ofVariants = getNumberOfVariants(entry.getValue());
            int originSize = allHyperParams.size();
            if (originSize==0 && ofVariants.count>0) {
                addInstances(allHyperParams, ofVariants.count);
            }
            else {
                for (int i = 0; i < ofVariants.count-1; i++) {
                    for (int j = 0; j < originSize; j++) {
                        HyperParams elem = allHyperParams.get(j);
                        allHyperParams.add(elem.asClone());
                    }
                }
            }
            for (HyperParams list : allHyperParams) {
                for (String value : ofVariants.values) {
                    if ( alreadyExists(allHyperParams, list, entry.getKey(), value)) {
                        continue;
                    }
                    list.put(entry.getKey(), value);
                    break;
                }
            }
        }
        return allHyperParams;
    }

    private static void addInstances(List<HyperParams> allHyperParams, int count) {
        for (int i = 0; i < count; i++) {
            allHyperParams.add(new HyperParams());
        }
    }

    private static boolean alreadyExists(List<HyperParams> allPaths, HyperParams hyper, String key, String value) {
        String path = hyper.path + ',' + key+':'+value;
        return allPaths.contains(new HyperParams(Consts.EMPTY_UNMODIFIABLE_MAP, path));
    }

    public static NumberOfVariants getNumberOfVariants(String variantsAsStr) {
        if (StringUtils.isBlank(variantsAsStr)) {
            return ZERO_VARIANT;
        }
        String s = variantsAsStr.trim();
        if ( s.charAt(0)!='(' && s.charAt(0)!='[' && !StringUtils.startsWithIgnoreCase(s.toLowerCase(), RANGE)) {
            final NumberOfVariants variants = new NumberOfVariants(true, null, 1);
            variants.values.add(s);
            return variants;
        }
        if (s.startsWith("[")) {
            if (!s.endsWith("]")) {
                return new NumberOfVariants(false, "Array definition must ends with ']'", 0);
            }
            s = s.substring(1, s.length()-1);
            int count = 0;
            final NumberOfVariants variants = new NumberOfVariants(true, null, 0);
            org.apache.commons.text.StringTokenizer st = new org.apache.commons.text.StringTokenizer( s, "," );
            st.setQuoteMatcher(StringMatcherFactory.INSTANCE.quoteMatcher());
            st.setTrimmerMatcher(StringMatcherFactory.INSTANCE.trimMatcher());
            for (String s1 : st.getTokenList()) {
                s1 = s1.trim();
                if (StringUtils.isBlank(s1)) {
                    continue;
                }

                variants.values.add(s1);
                count++;
            }
            variants.count = count;
            return variants;
        }
        String s1 = s;
        if (StringUtils.startsWithIgnoreCase(s1,RANGE)) {
            s1 = s1.substring(RANGE.length()).trim();
        }
        if (s1.charAt(0) == '(') {
            Scanner scanner = new Scanner(s1.substring(1));
            scanner.useDelimiter("[,)]");
            int start;
            int end;
            int change;
            try {
                start = Integer.parseInt(scanner.next().trim());
                end = Integer.parseInt(scanner.next().trim());
                change = Integer.parseInt(scanner.next().trim());
            } catch (NumberFormatException | NoSuchElementException e) {
                return new NumberOfVariants(false, "Wrong string format for string: " + s, 0);
            }

            int count = 0;
            final NumberOfVariants variants = new NumberOfVariants(true, null, 0);
            for (int i = start; i < end; i += change) {
                variants.values.add(Integer.toString(i));
                count++;
                if (count > 100) {
                    return new NumberOfVariants(false, "Too many variants for string: " + s, 0);
                }
            }
            variants.count = count;
            return variants;
        }
        return new NumberOfVariants(false, "Wrong number format for string: " + s, 0);
    }
}
