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

package ai.metaheuristic.ai.dispatcher.repositories;

import ai.metaheuristic.ai.dispatcher.beans.HeuristicEvaluation;
import org.springframework.context.annotation.Profile;
import org.springframework.data.repository.CrudRepository;

/**
 * @author Serge
 * Date: 5/4/2021
 * Time: 11:35 PM
 */
@Profile("dispatcher")
public interface HeuristicEvaluationRepository extends CrudRepository<HeuristicEvaluation, Long> {
}
