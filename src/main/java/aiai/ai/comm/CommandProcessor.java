/*
 * AiAi, Copyright (C) 2017-2018  Serge Maslyukov
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

package aiai.ai.comm;

import aiai.ai.beans.Station;
import aiai.ai.beans.StationExperimentSequence;
import aiai.ai.invite.InviteService;
import aiai.ai.launchpad.experiment.ExperimentService;
import aiai.ai.repositories.StationExperimentSequenceRepository;
import aiai.ai.repositories.StationsRepository;
import aiai.ai.station.StationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * User: Serg
 * Date: 12.08.2017
 * Time: 19:48
 */
@Service
@Slf4j
public class CommandProcessor {


    public static final int MAX_SEQUENSE_POOL_SIZE = 10;
    private final StationsRepository stationsRepository;
    private final StationService stationService;
    private final InviteService inviteService;
    private final ExperimentService experimentService;
    private final StationExperimentSequenceRepository stationExperimentSequenceRepository;

    public CommandProcessor(StationsRepository stationsRepository, StationService stationService, InviteService inviteService, ExperimentService experimentService, StationExperimentSequenceRepository stationExperimentSequenceRepository) {
        this.stationsRepository = stationsRepository;
        this.stationService = stationService;
        this.inviteService = inviteService;
        this.experimentService = experimentService;
        this.stationExperimentSequenceRepository = stationExperimentSequenceRepository;
    }

    public Command[] process(Command command) {
        switch (command.getType()) {
            case Nop:
                break;
            case ReportStation:
                break;
            case RequestStationId:
                return getNewStationId((Protocol.RequestStationId) command);
            case AssignedStationId:
                return storeStationId((Protocol.AssignedStationId) command);
            case ReAssignStationId:
                return reAssignStationId((Protocol.ReAssignStationId) command);
            case RegisterInvite:
                return processInvite((Protocol.RegisterInvite) command);
            case RegisterInviteResult:
                break;
            case RequestExperimentSequence:
                return processRequestExperimentSequence((Protocol.RequestExperimentSequence) command);
            case AssignedExperimentSequence:
                return processAssignedExperimentSequence((Protocol.AssignedExperimentSequence) command);
            case ReportStationEnv:
                return processReportStationEnv((Protocol.ReportStationEnv) command);
            case ReportSequenceProcessingResult:
                return processReportSequenceProcessingResult((Protocol.ReportSequenceProcessingResult) command);
            case ReportResultDelivering:
                return processReportResultDelivering((Protocol.ReportResultDelivering) command);
            default:
                System.out.println("There is new command which isn't processed: " + command.getType());
        }
        return Protocol.NOP_ARRAY;
    }

    private Command[] processReportResultDelivering(Protocol.ReportResultDelivering command) {
        stationService.markAsDelivered(command.getIds());
        return Protocol.NOP_ARRAY;
    }

    private Command[] processReportSequenceProcessingResult(Protocol.ReportSequenceProcessingResult command) {
        if (command.getResults().isEmpty()) {
            return Protocol.NOP_ARRAY;
        }
        final Protocol.ReportResultDelivering cmd1 = new Protocol.ReportResultDelivering(experimentService.storeAllResults(command.getResults()));
        // right now, sending new sequences with ReportResultDelivering doesn't work well.
//        final Protocol.AssignedExperimentSequence r = getAssignedExperimentSequence(command.getStationId(), Math.min(MAX_SEQUENSE_POOL_SIZE, command.getResults().size()));

        return new Command[]{cmd1};
    }

    private Command[] processReportStationEnv(Protocol.ReportStationEnv command) {
        checkStationId(command);
        final long stationId = Long.parseLong(command.getStationId());
        Station station = stationsRepository.findById(stationId).orElse(null);
        if (station==null) {
            // we throw ISE cos all checks have to be made early
            throw new IllegalStateException("Staion wasn't found for stationId: " + stationId );
        }
        station.setEnv( command.env);
        stationsRepository.save(station);
        return Protocol.NOP_ARRAY;
    }

    private Command[] processAssignedExperimentSequence(Protocol.AssignedExperimentSequence command) {
        if (command.sequences==null) {
            return Protocol.NOP_ARRAY;
        }
        for (Protocol.AssignedExperimentSequence.SimpleSequence sequence : command.sequences) {
            StationExperimentSequence seq = new StationExperimentSequence();
            seq.setCreatedOn(System.currentTimeMillis());
            seq.setParams(sequence.params);
            seq.setExperimentSequenceId(sequence.getExperimentSequenceId());
            stationExperimentSequenceRepository.save(seq);
        }
        return Protocol.NOP_ARRAY;
    }

    private Command[] processRequestExperimentSequence(Protocol.RequestExperimentSequence command) {
        checkStationId(command);
        Protocol.AssignedExperimentSequence r = getAssignedExperimentSequence(command.getStationId(), MAX_SEQUENSE_POOL_SIZE);
        return Protocol.asArray(r);
    }

    private synchronized Protocol.AssignedExperimentSequence getAssignedExperimentSequence(String stationId, int recordNumber) {
        Protocol.AssignedExperimentSequence r = new Protocol.AssignedExperimentSequence();
        r.sequences = experimentService.getSequncesAndAssignToStation(Long.parseLong(stationId), recordNumber);
        return r;
    }

    private void checkStationId(Command command) {
        if (command.getStationId()==null) {
            // we throw ISE cos all checks have to be made early
            throw new IllegalStateException("stationId is null");
        }
    }

    private Command[] storeStationId(Protocol.AssignedStationId command) {
        System.out.println("New station Id: " + command.getStationId());
        stationService.setStationId(command.getStationId());
        return Protocol.asArray(createReportStationEnvCommand());
    }

    private Command[] reAssignStationId(Protocol.ReAssignStationId command) {
        System.out.println("New station Id: " + command.getStationId());
        stationService.setStationId(command.getStationId());

        return Protocol.asArray(createReportStationEnvCommand());
    }

    private Command createReportStationEnvCommand() {
        String env = stationService.getEnv();
        return env==null ? Protocol.NOP : new Protocol.ReportStationEnv(env);
    }

    private Command[] processInvite(Protocol.RegisterInvite command) {
        Protocol.RegisterInviteResult result = new Protocol.RegisterInviteResult();
        result.setInviteResult(inviteService.processInvite(command.getInvite()));
        return Protocol.asArray(result);
    }

    private Command[] getNewStationId(Protocol.RequestStationId command) {
        final Station st = new Station();
        stationsRepository.save(st);

        return Protocol.asArray(new Protocol.AssignedStationId(Long.toString(st.getId())));
    }

    public ExchangeData processExchangeData(ExchangeData data) {
        return processExchangeData(null, data);
    }

    public ExchangeData processExchangeData(Map<String, String> sysParams, ExchangeData data) {
        ExchangeData responses = new ExchangeData();
        for (Command command : data.getCommands()) {
            if (command.getType()== Command.Type.Nop) {
                continue;
            }
            command.setSysParams(sysParams);
            responses.setCommands(process(command));
        }
        return responses;
    }
}
