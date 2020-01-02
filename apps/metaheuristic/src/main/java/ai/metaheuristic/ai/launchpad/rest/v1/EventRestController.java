/*
 * Metaheuristic, Copyright (C) 2017-2019  Serge Maslyukov
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

package ai.metaheuristic.ai.launchpad.rest.v1;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.exceptions.BinaryDataNotFoundException;
import ai.metaheuristic.ai.launchpad.event.LaunchpadEventService;
import ai.metaheuristic.ai.resource.ResourceWithCleanerInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.AbstractResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author Serge
 * Date: 10/16/2019
 * Time: 3:57 PM
 */
@RestController
@RequestMapping("/rest/v1/event")
@Slf4j
@Profile("launchpad")
@CrossOrigin
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('BILLING')")
public class EventRestController {

    private final LaunchpadEventService launchpadEventService;

    @GetMapping(value="/events-for-period/{periods}/events.zip", produces = "application/zip")
    public ResponseEntity<AbstractResource> getEventsForPeriod(HttpServletRequest request, @PathVariable String[] periods) throws IOException {
        log.warn("#467.010 Requested billing periods: " + Arrays.toString(periods));
        List<Integer> list = new ArrayList<>();
        for (String s : periods) {
            int period = Integer.parseInt(s);
            list.add(period);
            if (period<201900 || period>210000) {
                return new ResponseEntity<>(Consts.ZERO_BYTE_ARRAY_RESOURCE, HttpStatus.BAD_REQUEST);
            }
        }
        if (list.isEmpty()) {
            return new ResponseEntity<>(Consts.ZERO_BYTE_ARRAY_RESOURCE, HttpStatus.BAD_REQUEST);
        }

        final ResponseEntity<AbstractResource> entity;
        try {
            ResourceWithCleanerInfo resource = launchpadEventService.getEventsForPeriod(list);
            entity = resource.entity;
            if (resource.toClean!=null) {
                request.setAttribute(Consts.RESOURCES_TO_CLEAN, resource.toClean);
            }
        } catch (BinaryDataNotFoundException e) {
            return new ResponseEntity<>(Consts.ZERO_BYTE_ARRAY_RESOURCE, HttpStatus.GONE);
        }
        return entity;
    }

}
