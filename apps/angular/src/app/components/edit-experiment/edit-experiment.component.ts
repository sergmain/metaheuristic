import { animate, state, style, transition, trigger } from '@angular/animations';
import { Location } from '@angular/common';
import { Component, OnInit, ViewChild } from '@angular/core';
import { MatTableDataSource, MatDialog } from '@angular/material';
import { ActivatedRoute, Router } from '@angular/router';
import { CtWrapBlockComponent } from '@app/components/ct-wrap-block/ct-wrap-block.component';
import { DefaultResponse, Experiment } from '@app/models';
import { ExperimentsService, experiment, SimpleExperiment, HyperParams, HyperParam, SnippetResult, Snippet } from '@app/services/experiments/experiments.service';
import { Subscription } from 'rxjs';
import { ConfirmationDialogMethod } from '@app/components/app-dialog-confirmation/app-dialog-confirmation.component';

@Component({
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
    experimentEditResponse: experiment.edit.Response;
    simpleExperimentResponse: experiment.edit.Response;
    addHyperParamsResponse: DefaultResponse;
    editHyperParamsResponse: DefaultResponse;

    simpleExperiment: SimpleExperiment;

    hyperParams: HyperParams;
    newHyperParams: HyperParam = {};
    updatedHyperParams: HyperParam;
    currentEditHyperParams: HyperParam = {};

    hyperParamsDataSource = new MatTableDataSource([]);
    hyperParamsColumnsToDisplay = ['key', 'values', 'delete'];
    hyperParamsSecondColumnsToDisplay = ['empty', 'edit', 'done'];

    snippetResult: SnippetResult;
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
        private dialog: MatDialog
    ) {}

    ngOnInit() {
        this.loadExperimet();
    }

    loadExperimet() {
        const id: string = this.route.snapshot.paramMap.get('experimentId');
        const subscribe: Subscription = this.experimentsService.experiment.edit(id)
            .subscribe(
                (response: experiment.edit.Response) => {
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

    updateSimpleExperiment(event: Event) {
        const button: HTMLButtonElement = event.target as HTMLButtonElement;
        button.disabled = true;
        const subscribe: Subscription = this.experimentsService.experiment
            .editCommit(this.simpleExperiment)
            .subscribe(
                (response: experiment.edit.Response) => {
                    this.simpleExperimentResponse = response;
                },
                () => {},
                () => {
                    subscribe.unsubscribe();
                    button.disabled = false;
                }
            );
    }

    updateHyperParams(el, event) {
        this.metadataBlock.wait();
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
                this.loadExperimet();
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
                    this.loadExperimet();
                },
                () => {},
                () => {
                    subscribe.unsubscribe();
                }
            );
    }


    @ConfirmationDialogMethod({
        question: (hyperParam: HyperParam): string =>
            `Do you want to delete hyper\xa0param\xa0[${hyperParam.key}]`,
        rejectTitle: 'Cancel',
        resolveTitle: 'Delete'
    })
    deleteHyperParams(el) {
        this.metadataBlock.wait();
        const subscribe: Subscription = this.experimentsService.experiment
            .metadataDeleteCommit(this.simpleExperiment.id, el.id)
            .subscribe(
                () => this.loadExperimet(),
                () => {},
                () => subscribe.unsubscribe()
            );
    }

    addDefaultHyperParams() {
        this.metadataBlock.wait();
        let experimentId = this.simpleExperiment.id;
        let subscribe = this.experimentsService.experiment
            .metadataDefaultAddCommit(experimentId)
            .subscribe(
                () => {
                    this.loadExperimet();
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

    @ConfirmationDialogMethod({
        question: (snippet: Snippet): string =>
            `Do you want to delete snippet\xa0[${snippet.snippetCode}]`,
        rejectTitle: 'Cancel',
        resolveTitle: 'Delete'
    })
    snippetDeleteCommit(el) {
        this.snippetsBlock.wait();
        const subscribe: Subscription = this.experimentsService.experiment
            .snippetDeleteCommit(el.experimentId, el.id)
            .subscribe(
                (response: DefaultResponse) => {
                    this.snippetDeleteCommitResponse = response;
                    this.loadExperimet();
                },
                () => {},
                () => {
                    subscribe.unsubscribe();
                }
            );
    }

    snippetAddCommit(el) {
        this.snippetsBlock.wait();
        const experimentId = this.simpleExperiment.id;
        const data = {
            code: el.value
        };
        const subscribe = this.experimentsService.experiment
            .snippetAddCommit(experimentId, data)
            .subscribe(
                (response: DefaultResponse) => {
                    this.snippetAddCommitResponse = response;
                    this.loadExperimet();
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