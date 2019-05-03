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

package aiai.api.v1;

import lombok.ToString;

public class EnumsApi {

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

    public enum FlowValidateStatus { OK, NOT_VERIFIED_YET,
        FLOW_NOT_FOUND_ERROR,
        FLOW_INSTANCE_NOT_FOUND_ERROR,
        YAML_PARSING_ERROR,
        ALREADY_PRODUCED_ERROR,
        NOT_VALIDATED_YET_ERROR,
        FLOW_CODE_EMPTY_ERROR,
        FLOW_INSTANCE_DOESNT_EXIST_ERROR,
        NO_INPUT_POOL_CODE_ERROR,
        NO_ANY_PROCESSES_ERROR,
        INPUT_TYPE_EMPTY_ERROR,
        OUTPUT_TYPE_EMPTY_ERROR,
        NOT_ENOUGH_FOR_PARALLEL_EXEC_ERROR,
        SNIPPET_NOT_DEFINED_ERROR,
        FLOW_PARAMS_EMPTY_ERROR,
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
        PROCESS_VALIDATOR_NOT_FOUND_ERROR;

    }

    public enum FlowProducingStatus {
        OK,
        NOT_PRODUCING_YET_ERROR,
        EXPERIMENT_NOT_FOUND_BY_CODE_ERROR,
        PRODUCING_OF_EXPERIMENT_ERROR,
        INPUT_POOL_CODE_DOESNT_EXIST_ERROR,
        INPUT_POOL_CODE_FROM_META_DOESNT_EXIST_ERROR,
        FLOW_CODE_ALREADY_EXIST_ERROR,
        META_WASNT_CONFIGURED_FOR_EXPERIMENT_ERROR,
        FLOW_INSTANCE_NOT_FOUND_ERROR,
        FLOW_NOT_FOUND_ERROR,
        WRONG_FORMAT_OF_SNIPPET_CODE,
        ERROR,
    }
}
