import { Location } from '@angular/common';
import { Component, OnInit, ViewChild } from '@angular/core';
import { MatTableDataSource, MatButton } from '@angular/material';
import { CtTableComponent } from '@app/components/ct-table/ct-table.component';
import { ActivatedRoute } from '@angular/router';
import { LoadStates } from '@app/enums/LoadStates';
import { ExperimentInfoResponse } from '@app/models';
import { AtlasService } from '@app/services/atlas/atlas.service';
import { ExperimentsService, experiments, ExperimentItem } from '@app/services/experiments/experiments.service';
import { Experiment } from '@app/models';
import { Subscription } from 'rxjs';


@Component({
    selector: 'atlas',
    templateUrl: './atlas.component.pug',
    styleUrls: ['./atlas.component.scss']
})

export class AtlasComponent implements OnInit {
    readonly states = LoadStates;
    currentStates = new Set();
    experiment: ExperimentInfoResponse.Experiment;
    experimentInfo: ExperimentInfoResponse.ExperimentInfo;
    response: experiments.get.Response;

    dataSource = new MatTableDataSource < ExperimentItem > ([]);

    deletedExperiments: ExperimentItem[] = [];


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
        private location: Location
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
                (response: experiments.get.Response) => {
                    this.response = response;
                    this.dataSource = new MatTableDataSource(response.items.content || []);
                    this.prevTable.disabled = response.items.first;
                    this.nextTable.disabled = response.items.last;
                    this.table.show();
                    this.currentStates.delete(this.states.firstLoading);
                    this.currentStates.delete(this.states.loading);
                },
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

    // load() {
    //     const id = this.route.snapshot.paramMap.get('experimentId');
    //     const subscribe = this.atlasService.experiments.get()
    //         .subscribe(
    //             (response: ExperimentInfoResponse.Response) => {
    //                 this.experiment = response.experiment;
    //                 this.experimentInfo = response.experimentInfo;
    //                 this.tables.generalInfo.table = Object.keys(this.experiment).map(key => [key, this.experiment[key]]);
    //                 this.tables.hyperParameters.table = new MatTableDataSource(this.experiment.hyperParams);
    //                 this.tables.features.table = new MatTableDataSource(this.experimentInfo.features);
    //             },
    //             () => {},
    //             () => subscribe.unsubscribe()
    //         );
    // }
}