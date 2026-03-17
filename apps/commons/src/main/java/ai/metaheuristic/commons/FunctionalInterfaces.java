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

package ai.metaheuristic.commons;

import org.jspecify.annotations.Nullable;

/**
 * @author Sergio Lissner
 * Date: 11/1/2024
 * Time: 5:37 PM
 */
public final class FunctionalInterfaces {

    @FunctionalInterface
    public interface TripleConsumer<P, T, U> {
        void accept(P p, T t, U u);
    }

    @FunctionalInterface
    public interface TripleFunction<P, T, U, W> {
        W apply(P p, T t, U u);
    }

    @FunctionalInterface
    public interface QuadrupleConsumer<P, T, U, W> {
        void accept(P p, T t, U u, W w);
    }

    @FunctionalInterface
    public interface QuadrupleFunction<R, P, T, U, W> {
        W apply(R r, P p, T t, U u);
    }

    @FunctionalInterface
    public interface QuinaryConsumer<P, T, U, W, L> {
        void accept(P p, T t, U u, W w, L l);
    }

    @FunctionalInterface
    public interface QuinaryFunction<R, P, T, U, W, L> {
        L apply(R r, P p, T t, U u, W w);
    }

    @FunctionalInterface
    public interface HexagonalConsumer<P, T, U, W, L, O> {
        void accept(P p, T t, U u, W w, L l, O o);
    }

    @FunctionalInterface
    public interface HexagonalFunction<R, P, T, U, W, L, O> {
        O apply(R r, P p, T t, U u, W w, L l);
    }
}
