/*
 * Metaheuristic, Copyright (C) 2017-2023, Innovation platforms, LLC
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

package ai.metaheuristic.ai.dispatcher.internal_functions.aggregate;

import ai.metaheuristic.ai.dispatcher.variable.SimpleVariable;
import ai.metaheuristic.ai.dispatcher.variable.VariableTxService;
import ai.metaheuristic.ai.exceptions.InternalFunctionException;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.ww2003.CreateWW2003Document;
import ai.metaheuristic.ww2003.document.WW2003Document;
import ai.metaheuristic.ww2003.document.WW2003DocumentUtils;
import ai.metaheuristic.ww2003.document.persistence.ww2003.property.WW2003PropertyUtils;
import ai.metaheuristic.ww2003.document.tags.xml.Para;
import ai.metaheuristic.ww2003.document.tags.xml.Run;
import ai.metaheuristic.ww2003.document.tags.xml.Sect;
import ai.metaheuristic.ww2003.image.ImageConverterUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Base64;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.List;

import static ai.metaheuristic.ai.Enums.InternalFunctionProcessing.data_not_found;

/**
 * @author Sergio Lissner
 * Date: 5/28/2023
 * Time: 12:20 AM
 */
@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor
public class AggregateToWW2003Service {

    private final VariableTxService variableTxService;

    public void aggregate(Path tempFile, List<SimpleVariable> simpleVariables) {
        WW2003Document document = CreateWW2003Document.createWW2003Document();
        Sect sect = document.findBody().flatMap(body -> body.findFirst(Sect.class)).orElseThrow(()->new InternalFunctionException(data_not_found, "761.100 Section wasn't found"));

        int binaryIndex = 1;
        for (SimpleVariable v : simpleVariables) {

            final Run run = Run.t("Variable #" + v.id + ", name: " + v.variable);
            Para p = new Para(run);
            p.setShadow(true);
            WW2003PropertyUtils.addVanishRProp(run);
            WW2003PropertyUtils.addVanishRProp(p);
            sect.add(p);

            final EnumsApi.VariableType variableType = v.getDataStorageParams().type;
            EnumsApi.VariableType type = variableType==null ? EnumsApi.VariableType.unknown : variableType;
            if (type.isBinary) {
                byte[] bytes = variableTxService.getVariableAsBytes(v.id);
                String base64 = Base64.encodeBase64String(bytes);
                final Para para = ImageConverterUtils.getParaForImage(base64, binaryIndex++);
                sect.add(para);
            }
            else {
                String text = variableTxService.getVariableDataAsString(v.id);
                text.lines().forEach(line -> sect.add(new Para(Run.t(line))));

                sect.add(new Para());
            }
        }

        WW2003DocumentUtils.writeWW2003Document(tempFile, document);
    }
}
