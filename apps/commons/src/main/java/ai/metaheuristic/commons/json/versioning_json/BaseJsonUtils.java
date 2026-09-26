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

package ai.metaheuristic.commons.json.versioning_json;

import ai.metaheuristic.api.data.BaseParams;
import ai.metaheuristic.api.data.ParamsVersion;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.exceptions.ParamsProcessingException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.cfg.EnumFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.SerializationFeature;
import org.jspecify.annotations.Nullable;
import org.yaml.snakeyaml.error.YAMLException;

import java.util.Map;

/**
 * @author Serge
 * Date: 4/16/2021
 * Time: 5:20 PM
 */
public class BaseJsonUtils<T extends BaseParams> {

    private final ParamsJsonUtilsFactory FACTORY;

    private static final ObjectMapper mapper;
    static {
        ObjectMapper m = JsonMapper.builder()
                .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                // params classes keep their collections and nested objects in `public final` fields
                // (`public final List<X> items = new ArrayList<>()`). Jackson 3 leaves such fields untouched
                // by default, so a round trip silently comes back with empty collections. This restores
                // the Jackson 2 behaviour those classes were written against.
                .configure(MapperFeature.ALLOW_FINAL_FIELDS_AS_MUTATORS, true)
                // enums are written and read by name(). Jackson 3 defaults to toString(), which ties the stored
                // value to whatever toString() returns - a Lombok @ToString enum is stored as
                // "EnumsApi.DataSourcing.git(value=3)" and stops reading back the day that toString() changes.
                .configure(EnumFeature.WRITE_ENUMS_USING_TO_STRING, false)
                .configure(EnumFeature.READ_ENUMS_USING_TO_STRING, false)
                .build();
        mapper = m;
    }

    public static ObjectMapper getMapper() {
        return mapper;
    }


    public BaseJsonUtils(Map<Integer, AbstractParamsJsonUtils> map, AbstractParamsJsonUtils defJsonUtils) {
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

    public AbstractParamsJsonUtils getDefault() {
        return FACTORY.getDefault();
    }

    public String toString(BaseParams baseParams) {
        baseParams.checkIntegrity();
        return toStringInternal(baseParams);
    }

    public String toStringAsVersion(BaseParams baseParams, int version) {
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
        catch (JacksonException e) {
            throw new ParamsProcessingException("Error: " + e.getMessage(), e);
        }
    }

    public T to(String s) {
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
