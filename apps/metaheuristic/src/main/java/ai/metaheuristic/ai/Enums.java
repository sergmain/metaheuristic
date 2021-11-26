/*
 * Metaheuristic, Copyright (C) 2017-2021, Innovation platforms, LLC
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
package ai.metaheuristic.ai;

public final class Enums {

    public enum ProcessorAndSessionStatus { ok, reassignProcessor, newSession, updateSession }

    public enum QuotaAllocation { disabled, present }

    public enum DispatcherSelectionStrategy { alphabet, priority }

    public enum InternalFunctionProcessing {
        ok, function_not_found, source_code_not_found, source_code_is_broken,
        system_error, number_of_inputs_is_incorrect, number_of_outputs_is_incorrect,
        variable_not_found,
        global_variable_not_found,
        global_variable_is_immutable,
        variable_with_type_not_found,
        exec_context_not_found,
        exec_context_creation_error,
        exec_context_starting_error,
        process_not_found,
        sub_process_not_found,
        task_not_found,
        output_variable_not_defined, inline_not_found,
        number_of_metas_is_incorrect, meta_not_found,
        broken_graph_error,
        input_variable_isnt_file,
        not_supported_anymore
    }

    public enum AssetType { company, account, function, source}

    public enum FunctionState {
        // state is unknown, task for downloading of function is just created
        none(true),
        // asset file for function exists and we need to check checksum and signature, if they are presented
        ok(true),
        // function is ready for executing
        ready,

        not_found,
        not_supported_os,
        asset_error,
        download_error,
        function_config_error,
        io_error,
        dispatcher_config_error,
        signature_wrong,
        checksum_wrong,
        // not used anymore, left there for backward compatibility
        signature_not_found;

        public boolean needVerification = false;
        FunctionState() {
        }

        FunctionState(boolean needVerification) {
            this.needVerification = needVerification;
        }
    }

    /*
    public enum VerificationState { not_yet(false), error(true), ok(true);

        public boolean completed;

        VerificationState(boolean completed) {
            this.completed = completed;
        }
    }
*/

    public enum GitStatus {unknown, installed, not_found, error }

    public enum StoringStatus {OK, CANT_BE_STORED}

    public enum VariableState {none, ok, file_too_big, variable_doesnt_exist, unknown_error, transmitting_error, variable_cant_be_null }

    public enum UploadVariableStatus {
        OK,
        FILENAME_IS_BLANK,
        TASK_WAS_RESET,
        TASK_NOT_FOUND,
        VARIABLE_NOT_FOUND,
        UNRECOVERABLE_ERROR,
        PROBLEM_WITH_LOCKING,
        GENERAL_ERROR
    }

    public enum ResendTaskOutputResourceStatus {
        SEND_SCHEDULED, VARIABLE_NOT_FOUND, TASK_IS_BROKEN, TASK_NOT_FOUND, TASK_PARAM_FILE_NOT_FOUND, OUTPUT_RESOURCE_ON_EXTERNAL_STORAGE
    }

    @SuppressWarnings("unused")
    public enum FEATURE_STATUS {
        NONE(0), OK(1), ERROR(2), OBSOLETE(3);

        public final int value;

        FEATURE_STATUS(int value) {
            this.value = value;
        }
    }

    public enum FeatureExecStatus {
        unknown(0, "None"),
        processing(1, "Processing"),
        finished(2, "Finished"),
        finished_with_errors(3, "Finished with errors"),
        empty(4, "No tasks");

        public final int code;
        public final String info;

        FeatureExecStatus(int code, String info) {
            this.code = code;
            this.info = info;
        }

        public static FeatureExecStatus toState(int code) {
            switch (code) {
                case 0:
                    return unknown;
                case 1:
                    return processing;
                case 2:
                    return finished;
                case 3:
                    return finished_with_errors;
                case 4:
                    return empty;
                default:
                    return unknown;
            }
        }
        public boolean equals(String type) {
            return this.toString().equals(type);
        }
    }

    public enum BatchExecState {
        Error(-1, "Error"),
        Unknown(0, "None"),
        Stored(1, "Stored"),
        Preparing(2, "Preparing"),
        Processing(3, "Processing"),
        Finished(4, "Finished"),
        Archived(5, "Archived") ;

        public final int code;
        public final String info;

        @SuppressWarnings("DuplicateBranchesInSwitch")
        public static BatchExecState toState(int code) {
            switch (code) {
                case -1:
                    return Error;
                case 0:
                    return Unknown;
                case 1:
                    return Stored;
                case 2:
                    return Preparing;
                case 3:
                    return Processing;
                case 4:
                    return Finished;
                case 5:
                    return Archived;
                default:
                    return Unknown;
            }
        }

        BatchExecState(int code, String info) {
            this.code = code;
            this.info = info;
        }

        public boolean equals(String type) {
            return this.toString().equals(type);
        }
    }

//    public enum SignatureStates { unknown, signature_ok, signature_not_valid, not_signed }

    public enum LogType { ASSEMBLING(1), FEATURE(2), FIT(3), PREDICT(4), SEQUENCE(5),
        PRODUCING(6), PROCESSOR_LOG(7);

        public int typeNumber;

        LogType(int typeNumber) {
            this.typeNumber = typeNumber;
        }
    }

    public enum DispatcherLookupType {
        direct, registry
    }

    public enum AuthType {
        basic, oauth
    }
}
