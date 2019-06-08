import {
    Component,
    OnInit,
    ViewChild
} from '@angular/core';
import {
    ExperimentsService
} from '@app/services/experiments/experiments.service';
import {
    Location
} from '@angular/common';
import {
    ActivatedRoute
} from '@angular/router';
import {
    MatTableDataSource
} from '@angular/material';
import {
    PlotComponent
} from 'angular-plotly.js';
import {
    ExperimentFeaturePlotDataPartResponse,
    ExperimentFeatureProgressResponse,
    ExperimentFeatureProgressConsolePartResponse,
    DefaultResponse
} from '@app/models';
import {
    CtWrapBlockComponent
} from '@app/custom-tags/ct-wrap-block/ct-wrap-block.component';

@Component({
    selector: 'progress-experiment',
    templateUrl: './progress-experiment.component.pug',
    styleUrls: ['./progress-experiment.component.scss'],
})

export class ProgressExperimentComponent implements OnInit {

    @ViewChild(PlotComponent)
    @ViewChild('consoleView') consoleView: CtWrapBlockComponent;

    plotly: PlotComponent;

    response: ExperimentFeatureProgressResponse.Response;
    consolePartResponse: ExperimentFeatureProgressConsolePartResponse.Response;
    plotDataResponse: ExperimentFeaturePlotDataPartResponse.Response;

    experiment: ExperimentFeatureProgressResponse.Experiment;
    experimentId: string;
    featureId: string;
    pickedAxes: (boolean | any)[] = [false, false];
    currentTask: ExperimentFeatureProgressResponse.Task;

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
        let subscribe = this.experimentsService.experiment
            .featureProgress(this.experimentId, this.featureId)
            .subscribe((response: ExperimentFeatureProgressResponse.Response) => {
                this.response = response;
                this.tables.features.table = Object
                    .keys(response.experimentFeature)
                    .filter(key => ['resourceCodes', 'id', 'execStatus'].includes(key))
                    .map(key => [key, response.experimentFeature[key]]);
                this.tables.hyperParameters.table = new MatTableDataSource(response.hyperParamResult.elements);
                this.tables.metrics.table = new MatTableDataSource(response.metricsResult.metrics);
                this.tables.tasks.table = new MatTableDataSource(response.tasksResult.items.content);
                subscribe.unsubscribe();
            });
    }

    featureProgressPart(params) {
        let subscribe = this.experimentsService.experiment
            .featureProgressPart(this.experimentId, this.featureId, params)
            .subscribe((response: ExperimentFeatureProgressResponse.Response) => {
                this.tables.tasks.table = new MatTableDataSource(response.tasksResult.items.content);
            });
    }

    featureProgressConsolePart(taskId) {
        this.consoleView.wait();
        let subscribe = this.experimentsService.experiment
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
        let subscribe = this.experimentsService.experiment
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
    }

    featureProgressConsole(taskId) {
        let subscribe = this.experimentsService.experiment
            .featureProgressConsole(taskId)
            .subscribe(response => {
                subscribe.unsubscribe();
            });
    }

    featurePlotDataPart(params, paramsAxis) {
        let subscribe = this.experimentsService.experiment
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