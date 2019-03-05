import { Component, OnInit, ViewChild } from '@angular/core';
import { MatPaginator, MatTableDataSource } from '@angular/material';
import { Metadata, Experiment, ExperimentsService } from '@app/services/experiments/experiments.service'
import { Location } from '@angular/common'
import { ActivatedRoute } from '@angular/router'

@Component({
    selector: 'info-experiment',
    templateUrl: './info-experiment.component.pug',
    styleUrls: ['./info-experiment.component.scss']
})

export class InfoExperimentComponent implements OnInit {
    experiment: Experiment;
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
            columnsToDisplay: ['id', 'setOfFeatures', 'execStatus', 'maxValue', 'bts'],
        },
    }

    columnsToDisplay = ['id', 'name', 'createdOn', 'bts'];

    constructor(
        private route: ActivatedRoute,
        private experimentsService: ExperimentsService,
        private location: Location) {}

    ngOnInit() {
        const id = this.route.snapshot.paramMap.get('experimentId');
        this.experiment = this.experimentsService.getExperiment(id)
        this.tables.generalInfo.table = Object.keys(this.experiment)
            .map((key, i, a) => [key, this.experiment[key]])
        this.tables.hyperParameters.table = new MatTableDataSource(this.experiment.hyper)
        this.tables.features.table = new MatTableDataSource(this.experiment.features)

    }
}