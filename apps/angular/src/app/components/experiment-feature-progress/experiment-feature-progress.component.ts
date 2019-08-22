import { Location } from '@angular/common';
import { Component, OnInit, ViewChild } from '@angular/core';
import { MatTableDataSource } from '@angular/material';
import { ActivatedRoute } from '@angular/router';
import { DefaultResponse, ExperimentFeaturePlotDataPartResponse, ExperimentFeatureProgressConsolePartResponse } from '@app/models';
import { ExperimentsService, response, Experiment, Task, MetricsResult } from '@app/services/experiments';
import { CtWrapBlockComponent } from '@src/app/ct/ct-wrap-block/ct-wrap-block.component';
import { PlotComponent } from 'angular-plotly.js';
import { Subscription } from 'rxjs';

@Component({
    selector: 'experiment-feature-progress',
    templateUrl: './experiment-feature-progress.component.pug',
    styleUrls: ['./experiment-feature-progress.component.scss']
})

export class ExperimentFeatureProgressComponent implements OnInit {

    @ViewChild(PlotComponent)
    @ViewChild('consoleView') consoleView: CtWrapBlockComponent;

    plotly: PlotComponent;

    metricsResult: MetricsResult;
    canDraw: boolean = false;



    response: response.experiment.FeatureProgress;
    consolePartResponse: ExperimentFeatureProgressConsolePartResponse.Response;
    plotDataResponse: ExperimentFeaturePlotDataPartResponse.Response;

    experiment: Experiment;
    experimentId: string;
    featureId: string;
    pickedAxes: (boolean | any)[] = [false, false];
    currentTask: Task;

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
            columnsToDisplay: ['values', 'params']
        },
        tasks: {
            table: new MatTableDataSource([]),
            columnsToDisplay: ['id', 'col1', 'col2']
        },
        features: {
            table: [],
            columnsToDisplay: ['key', 'value']
        }
    };

    dataGraph = {
        show: false,
        initData: () => {
            this.dataGraph.data = [Object.assign({}, this.plotDataResponse || {}, {
                type: 'surface'
            })];
        },
        data: [],
        config: {
            scrollZoom: false,
            displayModeBar: true
        },
        layout: {
            title: 'Metrics',
            showlegend: false,
            autosize: true,
            scene: {
                xaxis: {
                    type: 'category',
                    title: ''
                },
                yaxis: {
                    type: 'category',
                    title: ''
                },
                zaxis: {
                    title: '',
                }
            }
        }
    };

    tasks: any;

    constructor(
        private route: ActivatedRoute,
        private experimentsService: ExperimentsService,
        private location: Location
    ) {}

    ngOnInit() {
        this.experimentId = this.route.snapshot.paramMap.get('experimentId');
        this.featureId = this.route.snapshot.paramMap.get('featureId');
        this.updateResponse();
    }

    updateResponse() {
        const subscribe: Subscription = this.experimentsService.experiment
            .featureProgress(this.experimentId, this.featureId)
            .subscribe((response: response.experiment.FeatureProgress) => {
                    this.response = response;
                    this.tables.features.table = Object
                        .keys(response.experimentFeature)
                        .filter(key => ['resourceCodes', 'id', 'execStatusAsString'].includes(key))
                        .map(key => [key, response.experimentFeature[key]]);
                    this.tables.hyperParameters.table = new MatTableDataSource(response.hyperParamResult.elements);
                    this.tables.metrics.table = new MatTableDataSource(response.metricsResult.metrics);
                    this.tables.tasks.table = new MatTableDataSource(response.tasksResult.items.content);

                    this.metricsResult = response.metricsResult;
                    this.tasks = response.tasksResult;
                },
                () => {},
                () => { subscribe.unsubscribe(); }
            );
    }

    featureProgressPart(params) {
        const subscribe: Subscription = this.experimentsService.experiment
            .featureProgressPart(this.experimentId, this.featureId, params)
            .subscribe((response: response.experiment.FeatureProgress) => {
                this.tables.tasks.table = new MatTableDataSource(response.tasksResult.items.content);
            });
    }

    featureProgressConsolePart(taskId) {
        this.consoleView.wait();
        const subscribe: Subscription = this.experimentsService.experiment
            .featureProgressConsolePart(taskId)
            .subscribe(
                (response: ExperimentFeatureProgressConsolePartResponse.Response) => {
                    this.consolePartResponse = response;
                },
                () => {},
                () => {
                    this.consoleView.show();
                    subscribe.unsubscribe();
                },
            );
    }

    taskRerun(taskId) {
        const subscribe: Subscription = this.experimentsService.experiment
            .taskRerun(taskId)
            .subscribe((response: DefaultResponse) => {
                this.updateResponse();
                subscribe.unsubscribe();
            });
    }

    pickHyperParam(el) {
        el.selected = !el.selected;
        let paramsArr = [];
        let params = this.response.hyperParamResult
            .elements.forEach((elem) => {
                elem.list.forEach((item, i) => {
                    if (item.selected) {
                        paramsArr.push(`${elem.key}-${i}`);
                    }
                });
            });
        this.featureProgressPart(paramsArr.join(','));
    }

    drawPlot() {
        let base = this;
        let params = initParams();
        let paramsAxis = initParamsAxis();

        function initParams() {
            let paramsArr = [];
            base.pickedAxes.forEach((elem) => {
                elem.list.forEach((item, i) => {
                    if (item.selected) {
                        paramsArr.push(`${elem.key}-${i}`);
                    }
                });
            });
            return paramsArr.join(',');
        }

        function initParamsAxis() {
            return base.pickedAxes.reduce((accm, elem) => {
                accm.push(elem.key);
                return accm;
            }, []).join(',');

        }
        console.log(params, paramsAxis);
        this.featurePlotDataPart(params || ',', paramsAxis);
    }

    pickAxis(el) {
        if (this.pickedAxes.includes(el)) {
            this.pickedAxes[this.pickedAxes.indexOf(el)] = false;
        } else {
            this.pickedAxes[this.pickedAxes.indexOf(false)] = el;
        }
        this.canDraw = !this.pickedAxes.includes(false);
    }

    featureProgressConsole(taskId) {
        const subscribe: Subscription = this.experimentsService.experiment
            .featureProgressConsole(taskId)
            .subscribe(response => {
                subscribe.unsubscribe();
            });
    }

    featurePlotDataPart(params, paramsAxis) {
        const subscribe: Subscription = this.experimentsService.experiment
            .featurePlotDataPart(this.experimentId, this.featureId, params, paramsAxis)
            .subscribe((response: ExperimentFeaturePlotDataPartResponse.Response) => {
                this.plotDataResponse = response;
                this.dataGraph.show = true;
                this.dataGraph.initData();
                console.log(this.dataGraph);
                subscribe.unsubscribe();
            });
    }
}