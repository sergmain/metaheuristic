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
package aiai.ai.station.actors;

import java.util.LinkedList;

public abstract class AbstractTaskQueue<T> {

    private final LinkedList<T> QUEUE = new LinkedList<>();

    public int queueSize(){
        synchronized (QUEUE) {
            return QUEUE.size();
        }
    }

    public void add(T t){
        synchronized (QUEUE) {
            if (!QUEUE.contains(t)) {
                QUEUE.add(t);
            }
        }
    }

    public T poll() {
        synchronized (QUEUE) {
            return QUEUE.pollFirst();
        }
    }
}
