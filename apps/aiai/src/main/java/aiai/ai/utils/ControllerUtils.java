/*
 * AiAi, Copyright (C) 2017-2019  Serge Maslyukov
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package aiai.ai.utils;

import aiai.ai.Consts;
import aiai.ai.launchpad.data.BaseDataClass;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.ui.Model;

import java.util.List;

public class ControllerUtils {

    public static Pageable fixPageSize(int limit, Pageable pageable) {
        if (pageable.getPageSize()!= limit) {
            pageable = PageRequest.of(pageable.getPageNumber(), limit);
        }
        return pageable;
    }

    public static void addMessagesToModel(Model model, BaseDataClass baseData) {
        if (CollectionUtils.isNotEmpty(baseData.errorMessages)) {
            List errorMessages = ((List)model.asMap().get(Consts.MODEL_ATTR_ERROR_MESSAGE));
            errorMessages.addAll(baseData.errorMessages);
        }
        if (CollectionUtils.isNotEmpty(baseData.infoMessages)) {
            List infoMessages = ((List)model.asMap().get(Consts.MODEL_ATTR_INFO_MESSAGES));
            infoMessages.addAll(baseData.infoMessages);
        }
    }
}
