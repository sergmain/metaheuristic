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

package ai.metaheuristic.commons.json.versioning_json;

import ai.metaheuristic.api.data.BaseParams;
import ai.metaheuristic.api.data.ParamsVersion;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.exceptions.ParamsProcessingException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.yaml.snakeyaml.error.YAMLException;

import java.util.Map;

/**
 * @author Serge
 * Date: 4/16/2021
 * Time: 5:20 PM
 */
public class BaseJsonUtils<T extends BaseParams> {

    @NonNull
    private final ParamsJsonUtilsFactory FACTORY;

    private static final ObjectMapper mapper;
    static {
        ObjectMapper m = new ObjectMapper();
        m.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        m.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper = m;
    }

    public static ObjectMapper getMapper() {
        return mapper;
    }


    public BaseJsonUtils(@NonNull Map<Integer, AbstractParamsJsonUtils> map, @NonNull AbstractParamsJsonUtils defJsonUtils) {
        map.forEach((k,v)-> {
            if (k!=v.getVersion()) {
                throw new IllegalStateException(S.f("Version is different, class: %s", v.getClass()));
            }
        });
        FACTORY = new ParamsJsonUtilsFactory(map, defJsonUtils);;
    }

    @Nullable
    public AbstractParamsJsonUtils getForVersion(int version) {
        return FACTORY.getForVersion(version);
    }

    @NonNull
    public AbstractParamsJsonUtils getDefault() {
        return FACTORY.getDefault();
    }

    @NonNull
    public String toString(@NonNull BaseParams baseParams) {
        baseParams.checkIntegrity();
        return toStringInternal(baseParams);
    }

    @NonNull
    public String toStringAsVersion(@NonNull BaseParams baseParams, int version) {
        AbstractParamsJsonUtils utils = getForVersion(version);
        if (utils==null) {
            throw new IllegalStateException("Unsupported version: " + version);
        }
        if (getDefault().getVersion()==version) {
            return toString(baseParams);
        }
        else {

            AbstractParamsJsonUtils jsonUtils = getDefault();
            Object currBaseParams = baseParams;
            do {
                if (jsonUtils.getVersion()==version) {
                    break;
                }
                //noinspection unchecked
                currBaseParams = jsonUtils.downgradeTo(currBaseParams);
            } while ((jsonUtils=(AbstractParamsJsonUtils)jsonUtils.prevUtil())!=null);

            //noinspection unchecked
            T p = (T)currBaseParams;

            return toStringInternal(p);
        }
    }

    private String toStringInternal(BaseParams baseParams) {
        try {
            return mapper.writeValueAsString(baseParams);
        }
        catch (JsonProcessingException e) {
            throw new ParamsProcessingException("Error: " + e.getMessage(), e);
        }
    }

    public T to(@NonNull String s) {
        try {
            ParamsVersion v = JsonForVersioning.getParamsVersion(s);
            AbstractParamsJsonUtils jsonUtils = getForVersion(v.getActualVersion());
            if (jsonUtils==null) {
                throw new IllegalStateException("Unsupported version: " + v.getActualVersion());
            }

            BaseParams currBaseParams = jsonUtils.to(s);
            do {
                //noinspection unchecked
                currBaseParams = jsonUtils.upgradeTo(currBaseParams);
            } while ((jsonUtils=(AbstractParamsJsonUtils)jsonUtils.nextUtil())!=null);

            //noinspection unchecked
            T p = (T)currBaseParams;

            p.checkIntegrity();

            return p;
        }
        catch (YAMLException e) {
            throw new ParamsProcessingException("Error: " + e.getMessage(), e);
        }
    }


}
