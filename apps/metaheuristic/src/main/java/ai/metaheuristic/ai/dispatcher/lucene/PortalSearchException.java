/*
 * Metaheuristic, Copyright (C) 2017-2024, Innovation platforms, LLC
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

package ai.metaheuristic.ai.dispatcher.lucene;

/**
 * User: SMaslyukov
 * Date: 01.06.2007
 * Time: 17:23:04
 */
public class PortalSearchException extends RuntimeException {

    public PortalSearchException(){
        super();
    }

    public PortalSearchException(String s){
        super(s);
    }

    public PortalSearchException(String s, Throwable cause){
        super(s, cause);
    }
}
