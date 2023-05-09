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

package ai.metaheuristic.ai.mhbp.commons_info;

import ai.metaheuristic.ai.mhbp.data.CommonInfoData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

/**
 * @author Sergio Lissner
 * Date: 2/28/2023
 * Time: 4:00 PM
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Profile("dispatcher")
public class CommonsInfoService {


    public CommonInfoData.Info getInfo(Pageable pageable) {
        CommonInfoData.Info info = new CommonInfoData.Info();
        return info;
    }
}
