import { Location } from '@angular/common';
import { Component, OnInit, ViewChild } from '@angular/core';
import { MatTableDataSource } from '@angular/material';
import { ActivatedRoute } from '@angular/router';
import { CtWrapBlockComponent } from '@app/components/ct-wrap-block/ct-wrap-block.component';
import { DefaultResponse, ExperimentFeaturePlotDataPartResponse, ExperimentFeatureProgressConsolePartResponse, ExperimentFeatureProgressResponse } from '@app/models';
import { PlotComponent } from 'angular-plotly.js';
import { AtlasService, experiment } from '@app/services/atlas/atlas.service';
import { Subscription } from 'rxjs';

@Component({
    selector: 'atlas-experiment-feature-progress',
    templateUrl: './atlas-experiment-feature-progress.component.pug',
    styleUrls: ['./atlas-experiment-feature-progress.component.scss']
})

export class AtlasExperimentFeatureProgressComponent implements OnInit {

    @ViewChild(PlotComponent)
    @ViewChild('consoleView') consoleView: CtWrapBlockComponent;

    plotly: PlotComponent;

    response: experiment.featureProgress.Response;
    consolePartResponse: ExperimentFeatureProgressConsolePartResponse.Response;
    plotDataResponse: ExperimentFeaturePlotDataPartResponse.Response;

    experiment: ExperimentFeatureProgressResponse.Experiment;

    atlasId: string;
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
        initData: (): void => {
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
        private atlasService: AtlasService,
        private location: Location
    ) {}

    ngOnInit() {
        this.atlasId = this.route.snapshot.paramMap.get('atlasId');
        this.experimentId = this.route.snapshot.paramMap.get('experimentId');
        this.featureId = this.route.snapshot.paramMap.get('featureId');
        this.updateResponse();
    }

    updateResponse() {
        const subscribe: Subscription = this.atlasService.experiment
            .featureProgress(this.atlasId, this.experimentId, this.featureId)
            .subscribe(
                (response: experiment.featureProgress.Response) => {
                    this.response = response;
                    this.tables.features.table = Object
                        .keys(response.experimentFeature)
                        .filter(key => ['resourceCodes', 'id', 'execStatus'].includes(key))
                        .map(key => [key, response.experimentFeature[key]]);
                    this.tables.hyperParameters.table = new MatTableDataSource(response.hyperParamResult.elements);
                    this.tables.metrics.table = new MatTableDataSource(response.metricsResult.metrics);
                    this.tables.tasks.table = new MatTableDataSource(response.tasks.content);
                },
                () => {},
                () => subscribe.unsubscribe()
            );
    }

    featureProgressPart(params) {
        const subscribe: Subscription = this.atlasService.experiment
            .featureProgressPart(this.atlasId, this.experimentId, this.featureId, params)
            .subscribe(
                (response: ExperimentFeatureProgressResponse.Response) => {
                    this.tables.tasks.table = new MatTableDataSource(response.tasksResult.items.content);
                },
                () => {},
                () => subscribe.unsubscribe()
            );
    }

    featureProgressConsolePart(taskId) {
        this.consoleView.wait();
        const subscribe: Subscription = this.atlasService.experiment
            .featureProgressConsolePart(this.atlasId, taskId)
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

    pickHyperParam(el) {
        el.selected = !el.selected;
        const paramsArr: string[] = [];
        const params = this.response.hyperParamResult
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
        const base = this;
        const params = initParams();
        const paramsAxis = initParamsAxis();

        function initParams() {
            const paramsArr: string[] = [];
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
        this.featurePlotDataPart(params || ',', paramsAxis);
    }

    pickAxis(el) {
        if (this.pickedAxes.includes(el)) {
            this.pickedAxes[this.pickedAxes.indexOf(el)] = false;
        } else {
            this.pickedAxes[this.pickedAxes.indexOf(false)] = el;
        }
    }

    featureProgressConsole(taskId: string) {
        const subscribe: Subscription = this.atlasService.experiment
            .featureProgressConsole(taskId)
            .subscribe(
                () => {},
                () => {},
                () => subscribe.unsubscribe()
            );
    }

    featurePlotDataPart(params, paramsAxis) {
        const subscribe: Subscription = this.atlasService.experiment
            .featurePlotDataPart(this.atlasId, this.experimentId, this.featureId, params, paramsAxis)
            .subscribe(
                (response: ExperimentFeaturePlotDataPartResponse.Response) => {
                    this.plotDataResponse = response;
                    this.dataGraph.show = true;
                    this.dataGraph.initData();
                },
                () => {},
                () => subscribe.unsubscribe()
            );
    }
}