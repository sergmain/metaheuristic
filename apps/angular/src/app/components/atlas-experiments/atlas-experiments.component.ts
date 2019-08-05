import { Location } from '@angular/common';
import { Component, OnInit, ViewChild } from '@angular/core';
import { MatButton, MatDialog, MatTableDataSource } from '@angular/material';
import { ActivatedRoute } from '@angular/router';
import { ConfirmationDialogMethod } from '@app/components/app-dialog-confirmation/app-dialog-confirmation.component';
import { LoadStates } from '@app/enums/LoadStates';
import { DefaultResponse, ExperimentInfoResponse } from '@app/models';
import { AtlasService, Experiment, response, ExperimentItem } from '@services/atlas/';
import { CtTableComponent } from '@src/app/ct/ct-table/ct-table.component';
import { Subscription } from 'rxjs';

@Component({
    selector: 'atlas-experiments',
    templateUrl: './atlas-experiments.component.pug',
    styleUrls: ['./atlas-experiments.component.scss']
})

export class AtlasExperimentsComponent implements OnInit {
    readonly states = LoadStates;
    currentStates = new Set();
    experiment: ExperimentInfoResponse.Experiment;
    experimentInfo: ExperimentInfoResponse.ExperimentInfo;
    response: response.experiments.Get;

    dataSource = new MatTableDataSource < ExperimentItem > ([]);

    deletedExperiments: Experiment[] = [];


    @ViewChild('nextTable') nextTable: MatButton;
    @ViewChild('prevTable') prevTable: MatButton;
    @ViewChild('table') table: CtTableComponent;


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

    columnsToDisplay = ['id', 'name', 'description', 'createdOn', 'bts'];

    constructor(
        private route: ActivatedRoute,
        private atlasService: AtlasService,
        private location: Location,
        private dialog: MatDialog
    ) {}

    ngOnInit() {
        this.currentStates.add(this.states.firstLoading);
        this.updateTable(0);
    }

    updateTable(page: number) {
        this.currentStates.add(this.states.loading);
        const subscribe: Subscription = this.atlasService.experiments
            .get(page)
            .subscribe(
                (response: response.experiments.Get) => {
                    this.response = response;
                    this.dataSource = new MatTableDataSource(response.items.content || []);
                    this.prevTable.disabled = response.items.first;
                    this.nextTable.disabled = response.items.last;
                    this.currentStates.delete(this.states.firstLoading);
                    this.currentStates.delete(this.states.loading);
                    this.table.show();
                },
                () => {},
                () => subscribe.unsubscribe()
            );
    }

    @ConfirmationDialogMethod({
        question: (experiment: Experiment): string =>
            `Do you want to delete Experiment\xa0#${experiment.id}`,
        rejectTitle: 'Cancel',
        resolveTitle: 'Delete'
    })
    delete(experiment: Experiment) {
        this.deletedExperiments.push(experiment);
        const subscribe: Subscription = this.atlasService.experiment
            .deleteCommit({ id: experiment.id })
            .subscribe(
                (response: DefaultResponse) => {},
                () => {},
                () => subscribe.unsubscribe()
            );
    }

    next() {
        this.prevTable.disabled = true;
        this.nextTable.disabled = true;
        this.updateTable(this.response.items.number + 1);
        this.table.wait();
    }

    prev() {
        this.prevTable.disabled = true;
        this.nextTable.disabled = true;
        this.updateTable(this.response.items.number - 1);
        this.table.wait();
    }
}