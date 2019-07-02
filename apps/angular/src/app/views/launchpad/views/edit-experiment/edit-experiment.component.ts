import {
    Component,
    OnInit,
    ViewChild
} from '@angular/core';
import {
    Location
} from '@angular/common';
import {
    MatTableDataSource
} from '@angular/material';
import {
    ExperimentsService
} from '@app/services/experiments/experiments.service';
import {
    ActivatedRoute,
    Router
} from '@angular/router';
import {
    animate,
    state,
    style,
    transition,
    trigger
} from '@angular/animations';
import {
    DefaultResponse,
    ExperimentEditResponse,
    ExperimentEditCommitResponse
} from '@app/models';
import {
    CtWrapBlockComponent
} from '@app/custom-tags/ct-wrap-block/ct-wrap-block.component';

@Component({
    // tslint:disable-next-line: component-selector
    selector: 'edit-experiment',
    templateUrl: './edit-experiment.component.pug',
    styleUrls: ['./edit-experiment.component.scss'],
    animations: [
        trigger('editMetadataCaption', [
            state('collapsed', style({
                'border-color': '*'
            })),
            state('expanded', style({
                'border-color': 'rgba(0,0,0,0)'
            })),
            transition('expanded <=> collapsed', animate('225ms cubic-bezier(0.4, 0.0, 0.2, 1)')),
        ]),
        trigger('editMetadataValue', [
            state('collapsed', style({
                height: '0px',
                minHeight: '0',
                display: 'none',
                opacity: '0'
            })),
            state('expanded', style({
                height: '*',
                opacity: '1'
            })),
            transition('expanded <=> collapsed', animate('225ms cubic-bezier(0.4, 0.0, 0.2, 1)')),
        ])
    ]
})

export class EditExperimentComponent implements OnInit {

    experimentEditResponse: ExperimentEditResponse.Response;
    simpleExperimentResponse: ExperimentEditResponse.Response;
    addHyperParamsResponse: DefaultResponse;
    editHyperParamsResponse: DefaultResponse;


    hyperParams: ExperimentEditResponse.HyperParams;
    simpleExperiment: ExperimentEditResponse.SimpleExperiment;

    newHyperParams: ExperimentEditResponse.ItemsEntity = {};
    updatedHyperParams: ExperimentEditResponse.ItemsEntity = {};

    currentEditHyperParams: ExperimentEditResponse.ItemsEntity = {};

    hyperParamsDataSource = new MatTableDataSource([]);
    hyperParamsColumnsToDisplay = ['key', 'values', 'delete'];
    hyperParamsSecondColumnsToDisplay = ['empty', 'edit', 'done'];



    snippetResult: ExperimentEditResponse.SnippetResult;
    snippetAddCommitResponse: DefaultResponse;
    snippetDeleteCommitResponse: DefaultResponse;
    currentSnippetAddCommit;
    snippetsDataSource = new MatTableDataSource([]);
    snippetsColumnsToDisplay = ['type', 'code', 'bts'];

    defaultMetadata = [
        ['epoch', '[10]'],
        ['RNN', '[LSTM, GRU, SimpleRNN]'],
        ['activation', '[hard_sigmoid, softplus, softmax, softsign, relu, tanh, sigmoid, linear, elu]'],
        ['optimizer', '[sgd, nadam, adagrad, adadelta, rmsprop, adam, adamax]'],
        ['batch_size', '[20, 40, 60]'],
        ['time_steps', '[5, 40, 60]'],
        ['metrics_functions', '[\'#in_top_draw_digit, accuracy\', \'accuracy\']']
    ];

    // snippets: any = false;
    // currentEditMetadata: Metadata | null;

    // newMetadata: Metadata = new Metadata('', '')
    @ViewChild('snippetsBlock') snippetsBlock: CtWrapBlockComponent;
    @ViewChild('metadataBlock') metadataBlock: CtWrapBlockComponent;


    constructor(
        private route: ActivatedRoute,
        private experimentsService: ExperimentsService,
        private location: Location,
        private router: Router,
    ) {}

    ngOnInit() {
        this.loadExperiment();
    }

    loadExperiment() {
        const id = this.route.snapshot.paramMap.get('experimentId');
        let subscribe = this.experimentsService.experiment.edit(id)
            .subscribe(
                (response: ExperimentEditResponse.Response) => {
                    this.experimentEditResponse = response;
                    this.simpleExperiment = response.simpleExperiment;

                    this.hyperParams = response.hyperParams;
                    this.hyperParamsDataSource = new MatTableDataSource(response.hyperParams.items);

                    this.snippetResult = response.snippetResult;
                    this.snippetsDataSource = new MatTableDataSource(response.snippetResult.snippets);

                },
                () => {},
                () => {
                    subscribe.unsubscribe();
                    if (this.snippetsBlock) {
                        this.snippetsBlock.show();
                    }
                    if (this.metadataBlock) {
                        this.metadataBlock.show();
                    }
                }
            );
    }

    updateSimpleExperiment(event) {
        console.log(event);
        event.target.disabled = true;
        const subscribe = this.experimentsService.experiment
            .editCommit(this.simpleExperiment)
            .subscribe(
                (response: ExperimentEditResponse.Response) => {
                    this.simpleExperimentResponse = response;
                },
                () => {},
                () => {
                    subscribe.unsubscribe();
                    event.target.disabled = false;
                }
            );
    }

    updateHyperParams(el, event) {
        this.metadataBlock.wait()
        if (!el.newValues) {
            return false;
        }
        let data = {
            id: el.id,
            key: el.key,
            value: el.newValues
        };
        let experimentId = this.simpleExperiment.id;
        let subscribe = this.experimentsService.experiment
            .metadataEditCommit(experimentId, data)
            .subscribe((response: DefaultResponse) => {
                this.editHyperParamsResponse = response;
                this.loadExperiment();
                subscribe.unsubscribe();
            });
    }

    addHyperParams() {
        this.metadataBlock.wait();
        let data = {
            id: this.simpleExperiment.id,
            key: this.newHyperParams.key,
            value: this.newHyperParams.values
        };
        let experimentId = this.simpleExperiment.id;
        let subscribe = this.experimentsService.experiment
            .metadataAddCommit(experimentId, data)
            .subscribe(
                (response: DefaultResponse) => {
                    this.addHyperParamsResponse = response;
                    this.loadExperiment();
                },
                () => {},
                () => {
                    subscribe.unsubscribe();
                }
            );
    }

    deleteHyperParams(el) {
        this.metadataBlock.wait();
        let subscribe = this.experimentsService.experiment
            .metadataDeleteCommit(this.simpleExperiment.id, el.id)
            .subscribe(
                () => {
                    this.loadExperiment();
                },
                () => {},
                () => {
                    subscribe.unsubscribe();
                }
            );
    }

    addDefaultHyperParams() {
        this.metadataBlock.wait();
        let experimentId = this.simpleExperiment.id;
        let subscribe = this.experimentsService.experiment
            .metadataDefaultAddCommit(experimentId)
            .subscribe(
                () => {
                    this.loadExperiment();
                },
                () => {},
                () => {
                    subscribe.unsubscribe();
                }
            );
    }


    openEditHyperParams(el) {
        if (this.currentEditHyperParams === el) {
            this.currentEditHyperParams = {};
        } else {
            this.currentEditHyperParams = el;
        }
    }

    snippetDeleteCommit(el) {
        this.snippetsBlock.wait();
        let subscribe = this.experimentsService.experiment
            .snippetDeleteCommit(el.experimentId, el.snippetCode)
            .subscribe(
                (response: DefaultResponse) => {
                    this.snippetDeleteCommitResponse = response;
                    this.loadExperiment();
                },
                () => {},
                () => {
                    subscribe.unsubscribe();
                }
            );
    }

    snippetAddCommit(el) {
        this.snippetsBlock.wait();
        let experimentId = this.simpleExperiment.id;
        let data = {
            code: el.value
        };
        let subscribe = this.experimentsService.experiment
            .snippetAddCommit(experimentId, data)
            .subscribe(
                (response: DefaultResponse) => {
                    this.snippetAddCommitResponse = response;
                    this.loadExperiment();
                },
                () => {},
                () => {
                    subscribe.unsubscribe();
                }
            );
    }

    save() {
        this.back();
    }

    back() {
        this.router.navigate(['/launchpad', 'experiments']);
    }

    cancel() {
        this.back();
    }

    // add() {
    //     let key = this.newMetadata.key.trim()
    //     let value = this.newMetadata.value.trim()
    //     if (key == '' || value == '') return false;
    //     this.experiment.metadatas.unshift(new Metadata(key, value))
    //     this.dataSource = new MatTableDataSource(this.experiment.metadatas);
    //     this.newMetadata = new Metadata('', '')

    // }

    // fill() {
    //     this.experiment.metadatas = this.experimentsService.setDefault(this.experiment.metadatas)
    //     this.dataSource = new MatTableDataSource(this.experiment.metadatas);
    // }

    // edit(el) {
    //     if (this.currentEditMetadata == el) return false;
    //     el.newValue = el.value
    //     this.currentEditMetadata = el
    // }

    // change(metadata, event) {
    //     event.stopPropagation()
    //     this.currentEditMetadata = null
    //     metadata.value = metadata.newValue
    //     delete metadata.newValue
    // }

    // delete(metadata, event) {
    //     event.stopPropagation()
    //     const i = this.experiment.metadatas.indexOf(metadata)
    //     this.experiment.metadatas.splice(i, 1)
    //     this.dataSource = new MatTableDataSource(this.experiment.metadatas);
    // }
}