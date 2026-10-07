/*
 * Metaheuristic, Copyright (C) 2017-2026, Innovation platforms, LLC
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

package ai.metaheuristic.ai.shutdown;

/**
 * Processor-side counterpart of {@link ShutdownInterface}. Every Spring bean implementing it is
 * informed about the shutdown by {@link ProcessorShutdownService}.
 *
 * @author Sergio Lissner
 * Date: 10/7/2026
 */
public interface ProcessorShutdownInterface {

    boolean isShutdown();

    void shutdown();
}
