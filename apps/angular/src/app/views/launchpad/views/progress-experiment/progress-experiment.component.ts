import { Component, OnInit, ViewChild } from '@angular/core';
import { Metadata, Experiment, ExperimentsService } from '@app/services/experiments/experiments.service'
import { Location } from '@angular/common'
import { ActivatedRoute } from '@angular/router'
import { MatTableDataSource } from '@angular/material';
import { PlotComponent } from 'angular-plotly.js';

@Component({
    selector: 'progress-experiment',
    templateUrl: './progress-experiment.component.pug',
    styleUrls: ['./progress-experiment.component.scss'],
})
export class ProgressExperimentComponent implements OnInit {

    @ViewChild(PlotComponent)
    plotly: PlotComponent



    experimentId: string
    featureId: string
    experiment: Experiment
    pickedAxes = []

    tables = {
        generalInfo: {
            table: new MatTableDataSource([]),
            columnsToDisplay: ['key', 'value']
        },
        hyperParameters: {
            table: new MatTableDataSource([]),
            columnsToDisplay: ['key', 'values', 'axes']
        },
        metrics: {
            table: new MatTableDataSource([]),
            columnsToDisplay: ['sum', 'params']
        },
        tasks: {
            table: new MatTableDataSource([]),
            columnsToDisplay: ['id', 'col1', 'col2']
        },
        features: {
            table: [],
            columnsToDisplay: ['key', 'value']
        }
    }

    graph = {
        data: [{
            // "x": ["10", "20"],
            // "y": ["bbb_1", "bbb_2"],
            "x": this.pickedAxes[0] ? this.pickedAxes[0].values : [0],
            "y": this.pickedAxes[1] ? this.pickedAxes[1].values : [0],
            "z": [
                [143, 159],
                [274, 319]
            ],
            type: 'surface'
        }],
        layout: {
            title: 'Metrics',
            showlegend: false,
            autosize: true,
            scene: {
                xaxis: { type: 'category', title: '' },
                yaxis: { type: 'category', title: '' },
                zaxis: { title: '' }
            }
        }
    };

    constructor(
        private route: ActivatedRoute,
        private experimentsService: ExperimentsService,
        private location: Location
    ) {}

    ngOnInit() {
        this.experimentId = this.route.snapshot.paramMap.get('experimentId');
        this.featureId = this.route.snapshot.paramMap.get('featureId');
        this.experiment = this.experimentsService.getExperiment(this.experimentId)

        this.tables.generalInfo.table = new MatTableDataSource([]);
        this.tables.hyperParameters.table = new MatTableDataSource(this.experiment.hyper)
        this.tables.metrics.table = new MatTableDataSource([]);
        this.tables.tasks.table = new MatTableDataSource(this.experiment.tasks);
        this.tables.features.table = Object.keys(this.experiment.features[0])
            .map((key, i, a) => [key, this.experiment.features[0][key]])
    }

    pickAxis(el) {
        let i = this.pickedAxes.indexOf(el)
        if (i + 1) {
            this.pickedAxes[i] = null
        } else {
            this.pickedAxes.unshift(el)
        }
        this.pickedAxes.length = 2
        this.graph.data = [{
            "x": this.pickedAxes[0] ? this.pickedAxes[0].values : [0],
            "y": this.pickedAxes[1] ? this.pickedAxes[1].values : [0],
            "z": [
                [143, 159],
                [274, 319]
            ],
            type: 'surface'
        }]
    }
}