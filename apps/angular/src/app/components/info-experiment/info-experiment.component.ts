import { Component, OnInit, ViewChild } from '@angular/core';
import { MatPaginator, MatTableDataSource } from '@angular/material';
import { ExperimentsService } from '@app/services/experiments/experiments.service';
import { Location } from '@angular/common';
import { ActivatedRoute } from '@angular/router';
import { state } from '@app/helpers/state';
import { ExperimentInfoResponse } from '@app/models';

@Component({
    selector: 'info-experiment',
    templateUrl: './info-experiment.component.pug',
    styleUrls: ['./info-experiment.component.scss']
})

export class InfoExperimentComponent implements OnInit {
    state = state;
    currentState = this.state.loading;
    experiment: ExperimentInfoResponse.Experiment;
    experimentInfo: ExperimentInfoResponse.ExperimentInfo;

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

    load() {
        const id = this.route.snapshot.paramMap.get('experimentId');
        let subscribe =  this.experimentsService.experiment.info(id)
            .subscribe((response: ExperimentInfoResponse.Response) => {
                this.experiment = response.experiment;
                this.experimentInfo = response.experimentInfo;
                this.tables.generalInfo.table = Object.keys(this.experiment).map(key => [key, this.experiment[key]]);
                this.tables.hyperParameters.table = new MatTableDataSource(this.experiment.hyperParams);
                this.tables.features.table = new MatTableDataSource(this.experimentInfo.features);
                subscribe.unsubscribe();
            });
    }
}