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

    private static class VariantProducer {
        String metaKey;
        String metaValues;
        int idx=0;

        boolean isCycle;

        private VariantProducer nextVariantProducer;
        NumberOfVariants ofVariants;

        public VariantProducer(boolean isCycle, String metaKey, String metaValues) {
            this.metaKey = metaKey;
            this.metaValues = metaValues;
            this.isCycle = isCycle;

            ofVariants = getNumberOfVariants(metaValues);
        }
        public void registerNext(VariantProducer variantProducer) {
            this.nextVariantProducer = variantProducer;
        }

        public Map<String, Long> next(Map<String, Long> map) {
            if (idx==ofVariants.values.size()) {
                if (isCycle) {
                    idx = 0;
                }
                else {
                    return null;
                }
            }
            map.put(metaKey, ofVariants.values.get(idx++));
            return nextVariantProducer!=null ? nextVariantProducer.next(map) : map;
        }
    }

    /**
     * Right now we support only java.util.Long type for variant
     */
    public static class MetaProducer {
        private VariantProducer variantProducer = null;

        public MetaProducer( Map<String, String> experimentMetadatas) {
            if (experimentMetadatas==null || experimentMetadatas.isEmpty()) {
                return;
            }

            List<Map.Entry<String, String>> entries = new ArrayList<>(experimentMetadatas.entrySet());

            Map.Entry<String, String> entry = entries.get(0);
            variantProducer = new VariantProducer(false, entry.getKey(), entry.getValue());

            if (experimentMetadatas.size()==1) {
                return;
            }
            VariantProducer whoPrev = variantProducer;
            for (int i = 1; i < entries.size(); i++) {
                entry = entries.get(i);
                final VariantProducer variantProducer = new VariantProducer(true, entry.getKey(), entry.getValue());
                whoPrev.registerNext(variantProducer);
                whoPrev = variantProducer;
            }
        }

        public Map<String, Long> next() {
            Map<String, Long> map = new LinkedHashMap<>();
            return variantProducer.next(map);
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
