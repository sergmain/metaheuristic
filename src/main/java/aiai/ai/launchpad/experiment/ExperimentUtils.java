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

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

import java.util.*;

public class ExperimentUtils {

    private static final String RANGE = "range";
    private static final NumberOfVariants ZERO_VARIANT = new NumberOfVariants(true, null, 0);

    @Data
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class NumberOfVariants {
        boolean status;
        String error;
        int count;
        final List<Long> values = new ArrayList<>();

        public static NumberOfVariants instanceOf(boolean status, String error, int count) {
            return new NumberOfVariants(status, error, count);
        }

        public static NumberOfVariants instanceOf(boolean status, String error, int count,  final List<Long> values ) {
            NumberOfVariants instance =  new NumberOfVariants(status, error, count);
            instance.values.addAll(values);
            return instance;
        }

        public static NumberOfVariants instanceOf(boolean status, String error, int count,  final Long[] values ) {
            NumberOfVariants instance =  new NumberOfVariants(status, error, count);
            instance.values.addAll(Arrays.asList(values));
            return instance;
        }

        public static NumberOfVariants instanceOf(boolean status, String error, int count,  final Long value ) {
            NumberOfVariants instance =  new NumberOfVariants(status, error, count);
            instance.values.add(value);
            return instance;
        }
    }

    public static List<Map<String, Long>> getAllPaths(Map<String, String> experimentMetadatas) {
        if (experimentMetadatas==null || experimentMetadatas.isEmpty()) {
            return new ArrayList<>();
        }

        List<Map<String, Long>> allPaths = new ArrayList<>();

        List<Map.Entry<String, String>> entries = new ArrayList<>(experimentMetadatas.entrySet());

        for (Map.Entry<String, String> entry : entries) {
            NumberOfVariants ofVariants = getNumberOfVariants(entry.getValue());
            int originSize = allPaths.size();
            if (originSize==0) {
                allPaths.add(new LinkedHashMap<>());
            }
            else {
                for (int i = 0; i < ofVariants.count-1; i++) {
                    for (int j = 0; j < originSize; j++) {
                        Map<String, Long> elem = allPaths.get(j);
                        allPaths.add(new LinkedHashMap<>(elem));
                    }
                }
            }
            VariantProducer variantProducer = new VariantProducer(entry.getKey(), ofVariants);
            for (Map<String, Long> list : allPaths) {
                for (Long value : ofVariants.values) {
                    if ( alreadyExists(allPaths, list, entry.getKey(), value)) {
                        continue;
                    }
                    list.put(entry.getKey(), value);
                }
            }
        }
        return allPaths;
    }

    private static boolean alreadyExists(List<Map<String, Long>> allPaths, Map<String, Long> map, String key, Long value) {
        Map<String, Long> newMap = new LinkedHashMap<>(map);
        newMap.put(key, value);
        for (Map<String, Long> allPath : allPaths) {
            if (allPath.equals(newMap)) {
                return true;
            }
        }
        return false;
    }


    private static class VariantProducer {
        int idx=0;

        private NumberOfVariants ofVariants;

        VariantProducer(String key, NumberOfVariants ofVariants) {
            this.ofVariants = ofVariants;
        }

        public Long next() {
            if (ofVariants.count==0) {
                return null;
            }
            if (idx==ofVariants.values.size()) {
                    idx = 0;
            }
            return ofVariants.values.get(idx++);
        }
    }

    public static NumberOfVariants getNumberOfVariants(String variantsAsStr) {
        if (StringUtils.isBlank(variantsAsStr)) {
            return ZERO_VARIANT;
        }
        String s = variantsAsStr.trim().toLowerCase();
        if (!StringUtils.startsWithAny(s, RANGE, "(", "[")) {
            long temp;
            try {
                temp = Long.parseLong(s);
            } catch (NumberFormatException e) {
                return new NumberOfVariants(false, "Wrong number format for string: " + s, 0);
            }

            final NumberOfVariants variants = new NumberOfVariants(true, null, 1);
            variants.values.add(temp);
            return variants;
        }
        if (s.startsWith("[")) {
            int count = 0;
            final NumberOfVariants variants = new NumberOfVariants(true, null, 0);
            for (StringTokenizer st = new StringTokenizer(s, "[,] "); st.hasMoreTokens(); ) {
                String token = st.nextToken();

                long temp;
                try {
                    temp = Long.parseLong(token);
                } catch (NumberFormatException e) {
                    return new NumberOfVariants(false, "Wrong number format for string: " + s, 0);
                }
                variants.values.add(temp);
                count++;
            }
            variants.count = count;
            return variants;
        }
        String s1 = s;
        if (s1.startsWith(RANGE)) {
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
                variants.values.add((long)i);
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
