/*
 AiAi, Copyright (C) 2017 - 2018, Serge Maslyukov

 This program is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program.  If not, see <https://www.gnu.org/licenses/>.

 */
package aiai.ai;

public final class Enums {
    public enum ExperimentExecState {
        NONE(0),            // just created experiment
        STARTED(1),         // started
        STOPPED(2),         // stopped
        FINISHED(3),        // finished
        DOESNT_EXIST(4);    // doesn't exist. this state is needed at station side to reconsile list of experiments

        public int code;

        ExperimentExecState(int code) {
            this.code = code;
        }

        public static ExperimentExecState toState(int code) {
            switch(code) {
                case 0:
                    return NONE;
                case 1:
                    return STARTED;
                case 2:
                    return STOPPED;
                case 3:
                    return FINISHED;
                case 4:
                    return DOESNT_EXIST;
                default:
                    return null;
            }
        }

        public static String from(int code) {
            ExperimentExecState state = toState(code);
            return state==null ? "Unknown" : state.toString();
        }
    }
}
