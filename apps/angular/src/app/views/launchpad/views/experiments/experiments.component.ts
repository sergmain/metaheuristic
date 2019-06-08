import {
    Component,
    OnInit,
    ViewChild
} from '@angular/core';
import {
    MatButton,
    MatTableDataSource
} from '@angular/material';
import {
    ExperimentsService
} from '@app/services/experiments/experiments.service';
import {
    LoadStates
} from '@app/enums/LoadStates';
import {
    DefaultResponse,
    ExperimentsResponse
} from '@app/models';
import {
    CtTableComponent
} from '@app/custom-tags/ct-table/ct-table.component';
import {
    Subscription
} from 'rxjs';

@Component({
    // tslint:disable-next-line: component-selector
    selector: 'experiments-view',
    templateUrl: './experiments.component.pug',
    styleUrls: ['./experiments.component.scss']
})

export class ExperimentsComponent implements OnInit {
    readonly states = LoadStates;
    currentStates = new Set();
    experimentsResponse: ExperimentsResponse.Response;
    dataSource = new MatTableDataSource < ExperimentsResponse.ContentEntity > ([]);
    columnsToDisplay = ['id', 'name', 'createdOn', 'bts'];

    deletedExperiments: (ExperimentsResponse.ContentEntity)[] = [];
    deleteCommitResponse: DefaultResponse;

    @ViewChild('nextTable') nextTable: MatButton;
    @ViewChild('prevTable') prevTable: MatButton;
    @ViewChild('table') table: CtTableComponent;

    constructor(
        private experimentsService: ExperimentsService
    ) {}

    ngOnInit() {
        this.currentStates.add(this.states.firstLoading);
        this.updateTable(0);
    }

    updateTable(page: number) {
        this.currentStates.add(this.states.loading);
        const subscribe: Subscription = this.experimentsService.experiments
            .get(page)
            .subscribe(
                (response: ExperimentsResponse.Response) => {
                    this.experimentsResponse = response;
                    this.dataSource = new MatTableDataSource(response.items.content || []);
                    this.prevTable.disabled = response.items.first;
                    this.nextTable.disabled = response.items.last;
                    this.table.show();
                    this.currentStates.delete(this.states.firstLoading);
                    this.currentStates.delete(this.states.loading);
                },
                () => {},
                () => {
                    subscribe.unsubscribe();
                }
            );
    }

    delete(el: ExperimentsResponse.ContentEntity) {
        this.deletedExperiments.push(el);
        const subscribe: Subscription = this.experimentsService.experiment
            .deleteCommit({
                id: el.id
            })
            .subscribe(
                (response: DefaultResponse) => {
                    // this.updateTable(this.experimentsResponse.items.number);
                },
                () => {},
                () => {
                    subscribe.unsubscribe();
                }
            );
    }

    clone(el: ExperimentsResponse.ContentEntity) {
        this.currentStates.add(this.states.loading);
        this.table.wait();
        const data: any = {
            id: el.id
        };
        const subscribe: Subscription = this.experimentsService.experiment
            .cloneCommit(data)
            .subscribe(
                (response: DefaultResponse) => {
                    this.updateTable(this.experimentsResponse.items.number);
                },
                () => {
                    this.updateTable(this.experimentsResponse.items.number);
                },
                () => {
                    subscribe.unsubscribe();
                }
            );
    }

    next() {
        this.prevTable.disabled = true;
        this.nextTable.disabled = true;
        this.updateTable(this.experimentsResponse.items.number + 1);
        this.table.wait();
    }

    prev() {
        this.prevTable.disabled = true;
        this.nextTable.disabled = true;
        this.updateTable(this.experimentsResponse.items.number - 1);
        this.table.wait();
    }
}