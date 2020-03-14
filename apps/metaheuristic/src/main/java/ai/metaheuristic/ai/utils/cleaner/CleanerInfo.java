/*
 * Metaheuristic, Copyright (C) 2017-2020  Serge Maslyukov
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

package ai.metaheuristic.ai.utils.cleaner;

import lombok.Data;
import org.springframework.core.io.AbstractResource;
import org.springframework.http.ResponseEntity;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Serge
 * Date: 10/2/2019
 * Time: 5:20 PM
 */
@Data
public class CleanerInfo {
    public ResponseEntity<AbstractResource> entity;
    public List<File> toClean = new ArrayList<>();
}
