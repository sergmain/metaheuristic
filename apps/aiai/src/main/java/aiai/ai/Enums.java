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

    public enum ProcessType {
        FILE_PROCESSING(1, false), EXPERIMENT(2, true);

        private int processType;
        private boolean snippetProvided;

        ProcessType(int processType, boolean snippetProvided) {
            this.processType = processType;
            this.snippetProvided = snippetProvided;
        }
    }

    public enum BinaryDataType { DATA(1), SNIPPET(2), TEST(3);

        public int value;

        BinaryDataType(int value) {
            this.value = value;
        }
    }

    public enum StoreData {
        DISK, DB
    }

    public enum TaskExecState {
        NONE(0),            // just created experiment
        STARTED(1),         // started
        STOPPED(2),         // stopped
        FINISHED(3),        // finished
        DOESNT_EXIST(4);    // doesn't exist. this state is needed at station side to reconsile list of experiments

        public int code;

        TaskExecState(int code) {
            this.code = code;
        }

        public static TaskExecState toState(int code) {
            switch (code) {
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
            TaskExecState state = toState(code);
            return state == null ? "Unknown" : state.toString();
        }
    }

    public enum FEATURE_STATUS {
        NONE(0), OK(1), ERROR(2), OBSOLETE(3);

        public final int value;

        FEATURE_STATUS(int value) {
            this.value = value;
        }
    }

    public enum FlowProducingStatus { OK,
        NOT_PRODUCING_YET_ERROR,
        EXPERIMENT_NOT_FOUND_BY_CODE_ERROR,
        INPUT_POOL_DOESNT_EXIST_ERROR,
        ERROR,
    }

    public enum FlowVerifyStatus { OK,
        NOT_VERIFIED_YET_ERROR,
        FLOW_CODE_EMPTY_ERROR,
        NO_INPUT_POOL_CODE_ERROR,
        NO_ANY_PROCESSES_ERROR,
        INPUT_TYPE_EMPTY_ERROR,
        NOT_ENOUGH_FOR_PARALLEL_EXEC_ERROR,
        SNIPPET_NOT_DEFINED_ERROR,
        FLOW_PARAMS_EMPTY_ERROR,
        SNIPPET_ALREADY_PROVIDED_BY_EXPERIMENT_ERROR,
        PROCESS_CODE_NOT_FOUND_ERROR,
        TOO_MANY_SNIPPET_CODES_ERROR,
        INPUT_CODE_NOT_SPECIFIED_ERROR,
        SNIPPET_NOT_FOUND_ERROR,
        EXPERIMENT_NOT_FOUND_ERROR,
        EXPERIMENT_META_NOT_FOUND_ERROR,
        EXPERIMENT_META_FEATURE_NOT_FOUND_ERROR,
        EXPERIMENT_META_DATASET_NOT_FOUND_ERROR,
        EXPERIMENT_META_ASSEMBLED_RAW_NOT_FOUND_ERROR,
        EXPERIMENT_MUST_BE_LAST_PROCESS_ERROR,
        RESOURCE_CODE_CONTAINS_ILLEGAL_CHAR_ERROR,
        PROCESS_CODE_CONTAINS_ILLEGAL_CHAR_ERROR,
    }

    public enum TaskProducingStatus { OK, VERIFY_ERROR, PRODUCING_ERROR }
}
