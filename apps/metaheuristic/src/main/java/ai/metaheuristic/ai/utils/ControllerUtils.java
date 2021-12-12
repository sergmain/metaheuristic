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
package ai.metaheuristic.ai.utils;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.api.data.BaseDataClass;
import org.springframework.ui.Model;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class ControllerUtils {

    public static void initRedirectAttributes(RedirectAttributes redirectAttributes, BaseDataClass r) {
        if (r.isErrorMessages()) {
            redirectAttributes.addFlashAttribute(Consts.MODEL_ATTR_ERROR_MESSAGE, r.getErrorMessagesAsList());
        }
        if (r.isInfoMessages()) {
            redirectAttributes.addFlashAttribute(Consts.MODEL_ATTR_INFO_MESSAGES, r.getInfoMessagesAsList());
        }
    }

    @SuppressWarnings("unchecked")
    public static void addMessagesToModel(Model model, BaseDataClass baseData) {
        Collection<?> collection1 = baseData.getErrorMessagesAsList();
        if (collection1 != null && !collection1.isEmpty()) {
            List errorMessages = ((List)model.asMap().get(Consts.MODEL_ATTR_ERROR_MESSAGE));
            if (errorMessages==null) {
                errorMessages = new ArrayList();
                model.addAttribute(Consts.MODEL_ATTR_ERROR_MESSAGE, errorMessages);
            }
            errorMessages.addAll(baseData.getErrorMessagesAsList());
        }
        Collection<?> collection = baseData.getInfoMessagesAsList();
        if (collection != null && !collection.isEmpty()) {
            List infoMessages = ((List)model.asMap().get(Consts.MODEL_ATTR_INFO_MESSAGES));
            if (infoMessages==null) {
                infoMessages = new ArrayList();
                model.addAttribute(Consts.MODEL_ATTR_INFO_MESSAGES, infoMessages);
            }
            infoMessages.addAll(baseData.getInfoMessagesAsList());
        }
    }
}
