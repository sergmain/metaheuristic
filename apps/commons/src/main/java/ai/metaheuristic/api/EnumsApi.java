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

package ai.metaheuristic.api;

import ai.metaheuristic.commons.CommonConsts;
import ai.metaheuristic.commons.S;
import lombok.ToString;

public class EnumsApi {

    public enum SnippetExecContext { external, internal }

    /**
     * local - all assets are managed locally
     * source - this launchpad is source for all assets
     * replicated - all assets on this launchpad are replicated from source launchpad
     */
    public enum LaunchpadAssetMode {local, source, replicated}

    public enum Fitting {

        UNDERFITTING, NORMAL, OVERFITTING;

        public static Fitting of(String s) {
            if (S.b(s)) {
                return null;
            }
            return valueOf(s);
        }
    }

    public enum ExperimentSnippet {
        FIT(CommonConsts.FIT_TYPE),
        PREDICT(CommonConsts.PREDICT_TYPE),
        CHECK_FITTING(CommonConsts.CHECK_FITTING_TYPE);

        public String code;

        ExperimentSnippet(String code) {
            this.code = code;
        }

        public static ExperimentSnippet to(String code) {
            switch (code) {
                case CommonConsts.FIT_TYPE:
                    return FIT;
                case CommonConsts.PREDICT_TYPE:
                    return PREDICT;
                case CommonConsts.CHECK_FITTING_TYPE:
                    return CHECK_FITTING;
                default:
                    throw new IllegalStateException("Unknown code: " + code);
            }
        }
    }
    
    public enum OS { unknown, any, windows, linux, macos }

    @ToString
    public enum DataSourcing {
        // data will be downloaded from launchpad
        launchpad(1),
        // snippet already has been deployed locally at station
        disk(2),
        // snippet will be downloaded from git
        git(3),
        // data will be provided via inline in params.yaml
        inline(4);

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
                case 4:
                    return inline;
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
        OUTPUT_VARIABLE_NOT_DEFINED_ERROR,
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
        FITTING_SNIPPET_NOT_FOUND_ERROR,
        VERSION_OF_SNIPPET_IS_TOO_LOW_ERROR,
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
        SOURCING_OF_VARIABLE_NOT_DEFINED_ERROR,
        START_VARIABLE_EMPTY_ERROR,
        START_RESOURCE_POOL_IS_EMPTY_ERROR,
        PROCESS_VALIDATOR_NOT_FOUND_ERROR,
        MIXED_EXTERNAL_AND_INTERNAL_SNIPPETS_ERROR,
        PRE_SNIPPET_WITH_INTERNAL_SNIPPET_ERROR,
        POST_SNIPPET_WITH_INTERNAL_SNIPPET_ERROR,
        INTERNAL_SNIPPET_WITH_PARALLEL_EXEC_ERROR,
        INTERNAL_AND_EXTERNAL_SNIPPET_IN_THE_SAME_PROCESS_ERROR,
        TOO_MANY_INTERNAL_SNIPPETS_ERROR,
        INTERNAL_SNIPPET_SUPPORT_ONLY_LAUNCHPAD_ERROR,
        INTERNAL_SNIPPET_NOT_FOUND_ERROR
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
        TOO_MANY_TASKS_PER_PLAN_ERROR
    }

    public enum BinaryDataType { DATA, SNIPPET }

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
        ERROR(-2),          // some error in configuration
        UNKNOWN(-1),        // unknown state
        NONE(0),            // just created workbook
        PRODUCING(1),       // producing was just started
        PRODUCED(2),        // producing was finished
        STARTED(3),         // started
        STOPPED(4),         // stopped
        FINISHED(5),        // finished
        DOESNT_EXIST(6),    // doesn't exist. this state is needed at station side to reconcile list of tasks
        EXPORTING_TO_ATLAS(7),    // workbook is marked as needed to be exported to atlas
        EXPORTING_TO_ATLAS_WAS_STARTED(8),    // workbook is marked as needed to be exported to atlas and export was started
        EXPORTED_TO_ATLAS(9);    // workbook was exported to atlas

        public int code;

        WorkbookExecState(int code) {
            this.code = code;
        }

        public static WorkbookExecState toState(int code) {
            switch (code) {
                case -2:
                    return ERROR;
                case -1:
                    //noinspection
                    return UNKNOWN;
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
                case 7:
                    return EXPORTING_TO_ATLAS;
                case 8:
                    return EXPORTING_TO_ATLAS_WAS_STARTED;
                case 9:
                    return EXPORTED_TO_ATLAS;
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

    public enum LaunchpadEventType {
        BATCH_FILE_UPLOADED, BATCH_CREATED, BATCH_PROCESSING_STARTED, BATCH_PROCESSING_FINISHED, BATCH_FINISHED_WITH_ERROR,
        TASK_ASSIGNED, TASK_FINISHED, TASK_ERROR

    }

    public enum MetricsStatus { NotFound, Ok, Error }
}
