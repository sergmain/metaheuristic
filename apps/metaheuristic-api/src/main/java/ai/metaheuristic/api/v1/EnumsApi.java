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

package ai.metaheuristic.api.v1;

import lombok.ToString;

public class EnumsApi {

    @ToString
    public enum DataSourcing {
        // data will be downloaded from launchpad
        launchpad(1),
        // snippet already has been deployed locally at station
        disk(2),
        // snippet will be downloaded from git
        git(3);

        public int value;

        DataSourcing(int value) {
            this.value = value;
        }

        @SuppressWarnings("Duplicates")
        public static DataSourcing to(int value) {
            switch (value) {
                case 1:
                    //noinspection
                    return launchpad;
                case 2:
                    return disk;
                case 3:
                    return git;
                default:
                    return launchpad;
            }
        }

        public static String from(int value) {
            //noinspection unused
            DataSourcing state = to(value);
            return state.toString();
        }
    }

    @ToString
    public enum SnippetSourcing {
        // snippet will be downloaded from launchpad
        launchpad(1),
        // snippet already has been deployed locally at station
        station(2),
        // snippet will be downloaded from git
        git(3);

        public int value;

        SnippetSourcing(int value) {
            this.value = value;
        }

        @SuppressWarnings("Duplicates")
        public static SnippetSourcing to(int value) {
            switch (value) {
                case 1:
                    //noinspection
                    return launchpad;
                case 2:
                    return station;
                case 3:
                    return git;
                default:
                    return launchpad;
            }
        }

        public static String from(int value) {
            //noinspection unused
            SnippetSourcing state = to(value);
            return state.toString();
        }

    }

    public enum ProcessType {
        FILE_PROCESSING(1), EXPERIMENT(2);

        public int value;

        ProcessType(int value) {
            this.value = value;
        }
    }

    public enum PlanValidateStatus { OK, NOT_VERIFIED_YET,
        PLAN_NOT_FOUND_ERROR,
        WORKBOOK_NOT_FOUND_ERROR,
        YAML_PARSING_ERROR,
        ALREADY_PRODUCED_ERROR,
        NOT_VALIDATED_YET_ERROR,
        PLAN_CODE_EMPTY_ERROR,
        WORKBOOK_DOESNT_EXIST_ERROR,
        NO_INPUT_POOL_CODE_ERROR,
        NO_ANY_PROCESSES_ERROR,
        INPUT_TYPE_EMPTY_ERROR,
        OUTPUT_TYPE_EMPTY_ERROR,
        NOT_ENOUGH_FOR_PARALLEL_EXEC_ERROR,
        SNIPPET_NOT_DEFINED_ERROR,
        PLAN_PARAMS_EMPTY_ERROR,
        PROCESS_PARAMS_EMPTY_ERROR,
        SNIPPET_ALREADY_PROVIDED_BY_EXPERIMENT_ERROR,
        PROCESS_CODE_NOT_FOUND_ERROR,
        TOO_MANY_SNIPPET_CODES_ERROR,
        INPUT_CODE_NOT_SPECIFIED_ERROR,
        WRONG_FORMAT_OF_SNIPPET_CODE,
        SNIPPET_NOT_FOUND_ERROR,
        EXPERIMENT_NOT_FOUND_ERROR,
        EXPERIMENT_ALREADY_STARTED_ERROR,
        EXPERIMENT_HASNT_ALL_SNIPPETS_ERROR,
        EXPERIMENT_META_NOT_FOUND_ERROR,
        EXPERIMENT_META_FEATURE_NOT_FOUND_ERROR,
        EXPERIMENT_META_DATASET_NOT_FOUND_ERROR,
        EXPERIMENT_META_ASSEMBLED_RAW_NOT_FOUND_ERROR,
        EXPERIMENT_MUST_BE_LAST_PROCESS_ERROR,
        RESOURCE_CODE_CONTAINS_ILLEGAL_CHAR_ERROR,
        PROCESS_CODE_CONTAINS_ILLEGAL_CHAR_ERROR,
        START_RESOURCE_POOL_CODE_EMPTY_ERROR,
        START_RESOURCE_POOL_IS_EMPTY_ERROR,
        PROCESS_VALIDATOR_NOT_FOUND_ERROR

    }

    public enum PlanProducingStatus {
        OK,
        NOT_PRODUCING_YET_ERROR,
        EXPERIMENT_NOT_FOUND_BY_CODE_ERROR,
        PRODUCING_OF_EXPERIMENT_ERROR,
        INPUT_POOL_CODE_DOESNT_EXIST_ERROR,
        INPUT_POOL_CODE_FROM_META_DOESNT_EXIST_ERROR,
        PLAN_CODE_ALREADY_EXIST_ERROR,
        META_WASNT_CONFIGURED_FOR_EXPERIMENT_ERROR,
        WORKBOOK_NOT_FOUND_ERROR,
        PLAN_NOT_FOUND_ERROR,
        WRONG_FORMAT_OF_SNIPPET_CODE,
        ERROR,
    }

    public enum BinaryDataType { UNKNOWN(0), DATA(1), SNIPPET(2), TEST(3), CONSOLE(4);

        public int value;
        BinaryDataType(int value) {
            this.value = value;
        }

        public static BinaryDataType from(int type) {
            switch(type) {
                case 1:
                    return DATA;
                case 2:
                    return SNIPPET;
                case 3:
                    return TEST;
                case 4:
                    return CONSOLE;
                default:
                    return UNKNOWN;
            }
        }

        @SuppressWarnings("unused")
        public boolean isProd() {
            return this==DATA || this==SNIPPET;
        }
    }

    public enum OperationStatus {OK, ERROR}

    public enum ExperimentTaskType {
        UNKNOWN(0), FIT(1), PREDICT(2);

        public int value;

        ExperimentTaskType(int value) {
            this.value = value;
        }

        public static ExperimentTaskType from(int type) {
            switch(type) {
                case 1:
                    return FIT;
                case 2:
                    return PREDICT;
                default:
                    return UNKNOWN;
            }
        }
    }

    public enum WorkbookExecState {
        NONE(0),            // just created workbook
        PRODUCING(1),       // producing was just started
        PRODUCED(2),        // producing was finished
        STARTED(3),         // started
        STOPPED(4),         // stopped
        FINISHED(5),        // finished
        DOESNT_EXIST(6),    // doesn't exist. this state is needed at station side to reconcile list of experiments
        UNKNOWN(-1),        // unknown state
        ERROR(-2);          // some error in configuration

        public int code;

        WorkbookExecState(int code) {
            this.code = code;
        }

        public static WorkbookExecState toState(int code) {
            switch (code) {
                case 0:
                    return NONE;
                case 1:
                    return PRODUCING;
                case 2:
                    return PRODUCED;
                case 3:
                    return STARTED;
                case 4:
                    return STOPPED;
                case 5:
                    return FINISHED;
                case 6:
                    return DOESNT_EXIST;
                case -1:
                    //noinspection
                    return UNKNOWN;
                case -2:
                    return ERROR;
                default:
                    return UNKNOWN;
            }
        }

        public static String from(int code) {
            //noinspection unused
            WorkbookExecState state = toState(code);
            return state.toString();
        }
    }

    public enum TaskExecState { NONE(0), IN_PROGRESS(1), ERROR(2), OK(3), BROKEN(4);

        public final int value;
        TaskExecState(int value) {
            this.value = value;
        }

        public static TaskExecState from(int type) {
            switch(type) {
                case 0:
                    return NONE;
                case 1:
                    return IN_PROGRESS;
                case 2:
                    return ERROR;
                case 3:
                    return OK;
                case 4:
                    return BROKEN;
                default:
                    throw new IllegalStateException("Unknown type : " + type);
            }
        }
    }

    public enum TaskProducingStatus { OK, VERIFY_ERROR, PRODUCING_ERROR }

    public enum Type {
        MD5(false), SHA256(false), SHA256WithSignature(true);

        public boolean isSign;

        Type(boolean isSign) {
            this.isSign = isSign;
        }

    }
}
