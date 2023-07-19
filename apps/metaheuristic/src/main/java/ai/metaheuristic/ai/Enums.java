/*
 * Metaheuristic, Copyright (C) 2017-2023, Innovation platforms, LLC
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

    public enum ApiKeySourceDefinedBy { none, server, user }

    public enum ExecContextInitState {NONE, DELTA, FULL}

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
        output_variable_not_defined, output_variable_not_found, inline_not_found,
        number_of_metas_is_incorrect,
        meta_not_found, name_of_variable_in_meta_is_broken,
        broken_graph_error,
        input_variable_isnt_file,
        not_supported_anymore,
        data_not_found,

        general_error
    }

    public enum AssetType { company, account, function, source}

    public enum GitStatus {unknown, processing, installed, not_found, error }

    public enum StoringStatus {OK, CANT_BE_STORED}

    public enum VariableState {none, ok, file_too_big, variable_doesnt_exist, unknown_error, transmitting_error, variable_cant_be_null, variable_is_null }

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

    public enum LogType { ASSEMBLING(1), FEATURE(2), FIT(3), PREDICT(4), SEQUENCE(5),
        PRODUCING(6), PROCESSOR_LOG(7);

        public final int typeNumber;

        LogType(int typeNumber) {
            this.typeNumber = typeNumber;
        }
    }

    public enum DispatcherLookupType { direct, registry }

    public enum AuthType { basic, token }

    public enum VariablesAs { permute, array; }

    public enum StringAsVariableSource { inline, variable; }

    // MHBP part

    public enum RequestCategory {math, social}

    public enum ResultStatus { usual, fail, problem }

    public enum RequestType {text, video, audio }
    public enum ResponseType {text, bool, digit }

    public enum TokenPlace { param, header }
    public enum PromptPlace { uri, text }
    public enum PromptResponseType {
        json(false, false), text(false, false), image(true, false),
        image_base64(true, true);

        public final boolean binary;
        public final boolean base64;
        PromptResponseType(boolean binary, boolean base64) {
            this.binary = binary;
            this.base64 = base64;
        }
    }
    public enum HttpMethodType { get, post }

    public enum QueryResultErrorType { cant_understand, common, server_error, query_too_long }

    public enum KbFileFormat { openai, mhbp, coqa, inline }

    public enum KbSourceInitStatus { not_yet, ready }

    public enum KbStatus { none(0), initiating(1), ready(2);
        public final int code;

        KbStatus(int code) {
            this.code = code;
        }

        public static KbStatus to(int code) {
            return switch (code) {
                case 0 -> none;
                case 1 -> initiating;
                case 2 -> ready;
                default -> throw new IllegalStateException("Unexpected value: " + code);
            };
        }
    }

    public enum SessionStatus { created(0), finished(1), finished_with_error(2);
        public final int code;

        SessionStatus(int code) {
            this.code = code;
        }
        public static SessionStatus to(int code) {
            return switch (code) {
                case 0 -> created;
                case 1 -> finished;
                case 2 -> finished_with_error;
                default -> throw new IllegalStateException("Unexpected value: " + code);
            };
        }
    }

    public enum AnswerStatus { normal(0), fail(1), error(2);
        public final int code;
        AnswerStatus(int code) {
            this.code = code;
        }
        public static AnswerStatus to(int status) {
            return switch (status) {
                case 0 -> normal;
                case 1 -> fail;
                case 2 -> error;
                default -> throw new IllegalStateException("Unexpected value: " + status);
            };
        }
    }

}
