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
import lombok.NoArgsConstructor;
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
        final List<String> values = new ArrayList<>();

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

    @Data
    @AllArgsConstructor
    public static class HyperParams {
        public Map<String, String> params = new LinkedHashMap<>();
        public String path;

        public HyperParams() {
            this.path = "";
        }

        public HyperParams asClone() {
            return new HyperParams(new LinkedHashMap<>(params), path);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            HyperParams that = (HyperParams) o;

            return path.equals(that.path);
        }

        @Override
        public int hashCode() {
            return path.hashCode();
        }

        void put(String key, String value) {
            params.put(key, value);
            path = path + ',' + key+':'+value;
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
                allHyperParams.add(new HyperParams());
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

    public static final Map<String, String> EMPTY_UNMODIFIABLE_MAP = Collections.unmodifiableMap(Collections.EMPTY_MAP);

    private static boolean alreadyExists(List<HyperParams> allPaths, HyperParams hyper, String key, String value) {
        String path = hyper.path + ',' + key+':'+value;
        return allPaths.contains(new HyperParams(EMPTY_UNMODIFIABLE_MAP, path));
/*

        for (HyperParams allPath : allPaths) {
            if (allPath.path.equals(path)) {
                return true;
            }
        }
        return false;
*/
    }

    public static NumberOfVariants getNumberOfVariants(String variantsAsStr) {
        if (StringUtils.isBlank(variantsAsStr)) {
            return ZERO_VARIANT;
        }
        String s = variantsAsStr.trim();
        if ( s.charAt(0)!='(' && s.charAt(0)!='[' && !StringUtils.startsWithIgnoreCase(s.toLowerCase(), RANGE)) {
/*
            long temp;
            try {
                temp = Long.parseLong(s);
            } catch (NumberFormatException e) {
                return new NumberOfVariants(false, "Wrong number format for string: " + s, 0);
            }
*/

            final NumberOfVariants variants = new NumberOfVariants(true, null, 1);
            variants.values.add(s);
            return variants;
        }
        if (s.startsWith("[")) {
            int count = 0;
            final NumberOfVariants variants = new NumberOfVariants(true, null, 0);
            for (StringTokenizer st = new StringTokenizer(s, "[,] "); st.hasMoreTokens(); ) {
                String token = st.nextToken();

/*
                long temp;
                try {
                    temp = Long.parseLong(token);
                } catch (NumberFormatException e) {
                    return new NumberOfVariants(false, "Wrong number format for string: " + s, 0);
                }
*/
                variants.values.add(token);
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
