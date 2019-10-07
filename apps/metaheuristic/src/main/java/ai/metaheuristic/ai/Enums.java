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
package ai.metaheuristic.ai;

public final class Enums {

    public enum SnippetState { none, ok, signature_wrong, signature_not_found, checksum_wrong, ready, not_supported_os,
        not_found, asset_error, download_error, snippet_config_error, io_error }

    public enum GitStatus {unknown, installed, not_found, error }

    public enum StoringStatus {OK, CANT_BE_STORED}

    public enum ResourceState {none, ok, file_too_big, resource_doesnt_exist, unknown_error, transmitting_error}

    public enum UploadResourceStatus {
        OK,
        FILENAME_IS_BLANK,
        TASK_WAS_RESET,
        TASK_NOT_FOUND,
        UNRECOVERABLE_ERROR,
        PROBLEM_WITH_LOCKING,
        GENERAL_ERROR
    }

    public enum Monitor { MEMORY }

    public enum ResendTaskOutputResourceStatus {
        SEND_SCHEDULED, RESOURCE_NOT_FOUND, TASK_IS_BROKEN, TASK_NOT_FOUND, TASK_PARAM_FILE_NOT_FOUND, OUTPUT_RESOURCE_ON_EXTERNAL_STORAGE
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
}
