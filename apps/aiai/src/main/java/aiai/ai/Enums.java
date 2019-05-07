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
package aiai.ai;

public final class Enums {

    public enum GitStatus {unknown, installed, not_found, error }

    public enum StoringStatus {OK, CANT_BE_STORED}

    public enum OperationStatus {OK, ERROR}

    public enum StorageType {launchpad, disk, hadoop, ftp }

    public enum UploadResourceStatus {
        OK,
        FILENAME_IS_BLANK,
        TASK_WAS_RESET,
        TASK_NOT_FOUND,
        PROBLEM_WITH_OPTIMISTIC_LOCKING,
        GENERAL_ERROR
    }

    public enum Monitor { MEMORY }

    public enum ResendTaskOutputResourceStatus {
        SEND_SCHEDULED, RESOURCE_NOT_FOUND, TASK_IS_BROKEN, TASK_PARAM_FILE_NOT_FOUND, OUTPUT_RESOURCE_ON_EXTERNAL_STORAGE
    }

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

    public enum TaskExecState { NONE(0), IN_PROGRESS(1), ERROR(2), OK(3);

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
                default:
                    throw new IllegalStateException("Unknown type : " + type);
            }
        }
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

    @SuppressWarnings("unused")
    public enum FEATURE_STATUS {
        NONE(0), OK(1), ERROR(2), OBSOLETE(3);

        public final int value;

        FEATURE_STATUS(int value) {
            this.value = value;
        }
    }

    public enum TaskProducingStatus { OK, VERIFY_ERROR, PRODUCING_ERROR }

    public enum FeatureExecStatus {
        unknown(0, "Unknown"), ok(1, "Ok"), error(2, "All are errors"), empty(3, "No tasks");

        public final int code;
        public final String info;

        FeatureExecStatus(int code, String info) {
            this.code = code;
            this.info = info;
        }

        public boolean equals(String type) {
            return this.toString().equals(type);
        }
    }
}
