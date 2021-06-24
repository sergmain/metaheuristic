/*
 * Metaheuristic, Copyright (C) 2017-2020, Innovation platforms, LLC
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

import ai.metaheuristic.commons.S;
import lombok.ToString;
import org.springframework.lang.Nullable;

public class EnumsApi {

    public enum BatchMappingKey {id, name }

    public enum SourceCodeLang { yaml }

    public enum SourceCodeType { not_exist, common, experiment, batch }

    public enum SourceCodeSubProcessLogic { and, or, sequential}

    public enum VariableContext {
        // this variable is global
        global,
        // this variable is local
        local,
        // this variable is an array of other variables, local and global
        array
    }

    public enum FunctionExecContext { external, internal, long_running }

    /**
     * local - all assets are managed locally
     * source - this dispatcher is source for all assets
     * replicated - all assets on this dispatcher are replicated from source dispatcher
     */
    public enum DispatcherAssetMode {local, source, replicated}

    public enum Fitting {

        UNKNOWN, UNDERFITTING, NORMAL, OVERFITTING;

        public static Fitting of(String s) {
            if (S.b(s)) {
                throw new IllegalStateException("Unknown Fitting enum: " + s);
            }
            return valueOf(s);
        }
    }

    public enum OS { unknown, any, windows, linux, macos }

    @ToString
    public enum DataSourcing {
        // data will be downloaded from dispatcher
        dispatcher(1),
        // data already has been deployed locally at processor
        disk(2),
        // data will be downloaded from git
        git(3),
        // data will be provided via inline as metadata
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
                    return dispatcher;
                case 2:
                    return disk;
                case 3:
                    return git;
                case 4:
                    return inline;
                default:
                    return dispatcher;
            }
        }

        public static String from(int value) {
            //noinspection unused
            DataSourcing state = to(value);
            return state.toString();
        }
    }

    @ToString
    public enum FunctionSourcing {
        // function will be downloaded from dispatcher
        dispatcher(1),
        // function already has been deployed locally at processor
        processor(2),
        // function will be downloaded from git
        git(3);

        public int value;

        FunctionSourcing(int value) {
            this.value = value;
        }

        @SuppressWarnings("Duplicates")
        public static FunctionSourcing to(int value) {
            switch (value) {
                case 1:
                    //noinspection
                    return dispatcher;
                case 2:
                    return processor;
                case 3:
                    return git;
                default:
                    return dispatcher;
            }
        }

        public static String from(int value) {
            //noinspection unused
            FunctionSourcing state = to(value);
            return state.toString();
        }

    }

    public enum SourceCodeValidateStatus { OK, NOT_VERIFIED_YET,
        SOURCE_CODE_NOT_FOUND_ERROR,
        EXEC_CONTEXT_NOT_FOUND_ERROR,
        YAML_PARSING_ERROR,
        ALREADY_PRODUCED_ERROR,
        NOT_VALIDATED_YET_ERROR,
        SOURCE_CODE_UID_EMPTY_ERROR,
        SOURCE_CODE_RECURSION_ERROR,
        SOURCE_CODE_PARAMS_EMPTY_ERROR,
        EXEC_CONTEXT_DOESNT_EXIST_ERROR,
        NO_ANY_PROCESSES_ERROR,
        INPUT_TYPE_EMPTY_ERROR,
        OUTPUT_VARIABLE_NOT_DEFINED_ERROR,
        INPUT_VARIABLE_NOT_DEFINED_ERROR,
        INPUT_VARIABLES_COUNT_MISMATCH_ERROR,
        OUTPUT_VARIABLES_COUNT_MISMATCH_ERROR,
        NOT_ENOUGH_FOR_PARALLEL_EXEC_ERROR,
        FUNCTION_NOT_DEFINED_ERROR,
        PROCESS_CODE_NOT_UNIQUE_ERROR,
        PROCESS_PARAMS_EMPTY_ERROR,
        FUNCTION_ALREADY_PROVIDED_BY_EXPERIMENT_ERROR,
        PROCESS_CODE_NOT_FOUND_ERROR,
        TOO_MANY_FUNCTION_CODES_ERROR,
        INPUT_CODE_NOT_SPECIFIED_ERROR,
        FUNCTION_NOT_FOUND_ERROR,
        FITTING_FUNCTION_NOT_FOUND_ERROR,
        VERSION_OF_FUNCTION_IS_TOO_LOW_ERROR,
        EXPERIMENT_NOT_FOUND_ERROR,
        EXPERIMENT_ALREADY_STARTED_ERROR,
        EXPERIMENT_HASNT_ALL_FUNCTIONS_ERROR,
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
        MIXED_EXTERNAL_AND_INTERNAL_FUNCTIONS_ERROR,
        PRE_FUNCTION_WITH_INTERNAL_FUNCTION_ERROR,
        POST_FUNCTION_WITH_INTERNAL_FUNCTION_ERROR,
        INTERNAL_FUNCTION_WITH_PARALLEL_EXEC_ERROR,
        INTERNAL_AND_EXTERNAL_FUNCTION_IN_THE_SAME_PROCESS_ERROR,
        TOO_MANY_INTERNAL_FUNCTIONS_ERROR,
        INTERNAL_FUNCTION_SUPPORT_ONLY_DISPATCHER_ERROR,
        INTERNAL_FUNCTION_NOT_FOUND_ERROR,
        WRONG_CODE_OF_PROCESS_ERROR,
        DYNAMIC_OUTPUT_SUPPORTED_ONLY_FOR_INTERNAL_ERROR,
        WRONG_FORMAT_OF_FUNCTION_CODE_ERROR,
        WRONG_FORMAT_OF_VARIABLE_NAME_ERROR,
        WRONG_FORMAT_OF_INLINE_VARIABLE_ERROR,
        CACHING_ISNT_SUPPORTED_FOR_INTERNAL_FUNCTION_ERROR,
        SUB_PROCESS_LOGIC_NOT_DEFINED,
        META_NOT_FOUND_ERROR,
        STRICT_NAMING_REQUIRED_ERROR
    }

    public enum TaskProducingStatus {
        OK,
        NOT_PRODUCING_YET_ERROR,
        TASK_PRODUCING_ERROR,
        EXPERIMENT_NOT_FOUND_BY_CODE_ERROR,
        PRODUCING_OF_EXPERIMENT_ERROR,
        INPUT_VARIABLE_DOESNT_EXIST_ERROR,
        INPUT_VARIABLE_FROM_META_DOESNT_EXIST_ERROR,
        SOURCE_CODE_UID_ALREADY_EXIST_ERROR,
        META_WASNT_CONFIGURED_FOR_EXPERIMENT_ERROR,
        EXEC_CONTEXT_NOT_FOUND_ERROR,
        SOURCE_CODE_NOT_FOUND_ERROR,
        PROCESS_NOT_FOUND_ERROR,
        WRONG_FORMAT_OF_FUNCTION_CODE,
        ERROR,
        TOO_MANY_TASKS_PER_SOURCE_CODE_ERROR,
        TOO_MANY_LEVELS_OF_SUBPROCESSES_ERROR,
        INTERNAL_FUNCTION_DECLARED_AS_EXTERNAL_ERROR
    }

    public enum DataType {variable, global_variable, function}

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

    public enum ExecContextState {
        ERROR(-2),          // some error in configuration
        UNKNOWN(-1),        // unknown state
        NONE(0),            // just created execContext
        PRODUCING(1),       // producing was just started
        NOT_USED_ANYMORE(2),        // former 'PRODUCED' status
        STARTED(3),         // started
        STOPPED(4),         // stopped
        FINISHED(5),        // finished
        DOESNT_EXIST(6);    // doesn't exist. this state is needed at processor side to reconcile list of tasks

        public int code;

        ExecContextState(int code) {
            this.code = code;
        }

        public static ExecContextState toState(int code) {
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
                    return NOT_USED_ANYMORE;
                case 3:
                    return STARTED;
                case 4:
                    return STOPPED;
                case 5:
                    return FINISHED;
                case 6:
                    return DOESNT_EXIST;
                default:
                    return UNKNOWN;
            }
        }

        public static ExecContextState from(String state) {
            try {
                return valueOf(state);
            }
            catch(Throwable th) {
                return UNKNOWN;
            }
        }

        public static String from(int code) {
            //noinspection unused
            ExecContextState state = toState(code);
            return state.toString();
        }

        public static ExecContextState fromCode(int code) {
            ExecContextState state = toState(code);
            return state;
        }

        public static boolean isFinishedState(int state) {
            return isFinishedState(fromCode(state));
        }

        public static boolean isFinishedState(ExecContextState state) {
            return state==FINISHED || state==ERROR;
        }
    }

    public enum TaskExecState { NONE(0), IN_PROGRESS(1),
        ERROR(2), OK(3), NOT_USED_ANYMORE(4),
        SKIPPED(5), CHECK_CACHE(6);

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
                    //noinspection
                    return ERROR;
                case 5:
                    return SKIPPED;
                case 6:
                    return CHECK_CACHE;
                default:
                    throw new IllegalStateException("Unknown type : " + type);
            }
        }

        public static boolean isFinishedState(int state) {
            return isFinishedState(from(state));
        }

        public static boolean isFinishedState(TaskExecState state) {
            return state==EnumsApi.TaskExecState.OK || state==EnumsApi.TaskExecState.ERROR || state==EnumsApi.TaskExecState.SKIPPED;
        }
    }

    public enum HashAlgo {
        MD5(false, null), SHA256(false, null), SHA256WithSignature(true, "SHA256withRSA");

        public final boolean isSigned;

        @Nullable
        public final String signatureAlgo;

        HashAlgo(boolean isSigned, @Nullable String signatureAlgo) {
            this.isSigned = isSigned;
            this.signatureAlgo = signatureAlgo;
        }

    }

    public enum DispatcherEventType {
        BATCH_FILE_UPLOADED,
        BATCH_CREATED,
        BATCH_PROCESSING_STARTED,   // processing time doesn't include the time for creating tasks
        BATCH_PROCESSING_FINISHED,
        BATCH_FINISHED_WITH_ERROR,
        TASK_ASSIGNED,
        TASK_FINISHED,
        TASK_ERROR

    }

    public enum MetricsStatus { NotFound, Ok, Error }

    public enum ChecksumState { not_yet, not_presented, correct, wrong, runtime }

    public enum SignatureState { not_yet, not_presented, correct, wrong, runtime }
}
