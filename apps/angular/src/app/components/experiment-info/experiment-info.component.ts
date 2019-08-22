import { Location } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { MatTableDataSource } from '@angular/material';
import { ActivatedRoute } from '@angular/router';
import { state } from '@app/helpers/state';
import { DefaultResponse, ExperimentInfoResponse } from '@app/models';
import { ExperimentsService } from '@app/services/experiments/experiments.service';
import { Subscription } from 'rxjs';

@Component({
    selector: 'experiment-info',
    templateUrl: './experiment-info.component.pug',
    styleUrls: ['./experiment-info.component.scss']
})

export class ExperimentInfoComponent implements OnInit {
    state = state;
    currentState = this.state.loading;

    response: ExperimentInfoResponse.Response;
    experiment: ExperimentInfoResponse.Experiment;
    experimentInfo: ExperimentInfoResponse.ExperimentInfo;

    toAtlasResponse: DefaultResponse;

    tables = {
        generalInfo: {
            table: [],
            columnsToDisplay: ['key', 'value'],
        },
        hyperParameters: {
            table: new MatTableDataSource([]),
            columnsToDisplay: ['key', 'value', 'variants'],
        },
        features: {
            table: new MatTableDataSource([]),
            columnsToDisplay: ['id', 'resourceCodes', 'execStatus', 'maxValue', 'bts'],
        },
    };

    columnsToDisplay = ['id', 'name', 'createdOn', 'bts'];

    constructor(
        private route: ActivatedRoute,
        private experimentsService: ExperimentsService,
        private location: Location
    ) {}

    ngOnInit() {
        this.load();
    }

    toAtlas() {
        const subscribe: Subscription = this.experimentsService.experiment
            .toAtlas(this.experiment.id.toString())
            .subscribe(
                (response: DefaultResponse) => {
                    this.toAtlasResponse = response;
                },
                () => {},
                () => subscribe.unsubscribe()
            );
    }



    stop() {

    }

    load() {
        const id = this.route.snapshot.paramMap.get('experimentId');
        const subscribe: Subscription = this.experimentsService.experiment.info(id)
            .subscribe(
                (response: ExperimentInfoResponse.Response) => {
                    this.response = response;
                    this.experiment = response.experiment;
                    this.experimentInfo = response.experimentInfo;
                    this.tables.generalInfo.table = Object.keys(this.experiment).map(key => [key, this.experiment[key]]);
                    this.tables.hyperParameters.table = new MatTableDataSource(this.experiment.hyperParams);
                    this.tables.features.table = new MatTableDataSource(this.experimentInfo.features);
                },
                () => {},
                () => subscribe.unsubscribe()
            );
    }
}