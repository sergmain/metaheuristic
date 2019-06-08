import {
    Component,
    OnInit,
    ViewChild,
} from '@angular/core';
import {
    FlowsService
} from '@app/services/flows/flows.service';
import {
    MatTableDataSource,
    MatButton
} from '@angular/material';
import {
    LoadStates
} from '@app/enums/LoadStates';
import {
    FlowsResponse
} from '@app/models';
import {
    CtTableComponent
} from '@app/custom-tags/ct-table/ct-table.component';
import {
    Subscription
} from 'rxjs';

@Component({
    // tslint:disable-next-line: component-selector
    selector: 'flows-view',
    templateUrl: './flows.component.pug',
    styleUrls: ['./flows.component.scss']
})

export class FlowsComponent implements OnInit {
    readonly states = LoadStates;
    currentStates = new Set();
    response: FlowsResponse.Response;
    dataSource = new MatTableDataSource < FlowsResponse.Flow > ([]);
    columnsToDisplay = ['id', 'code', 'createdOn', 'valid', 'locked', 'bts'];
    deletedFlows: (FlowsResponse.Flow)[] = [];
    @ViewChild('nextTable') nextTable: MatButton;
    @ViewChild('prevTable') prevTable: MatButton;
    @ViewChild('table') table: CtTableComponent;

    constructor(
        private flowsService: FlowsService
    ) {}

    ngOnInit() {
        this.currentStates.add(this.states.firstLoading);
        this.updateTable(0);
    }

    updateTable(page: number) {
        this.currentStates.add(this.states.loading);
        const subscribe: Subscription = this.flowsService.flows.get({
                page
            })
            .subscribe(
                (response: FlowsResponse.Response) => {
                    this.response = response;
                    this.dataSource = new MatTableDataSource(response.items.content || []);
                },
                () => {},
                () => {
                    this.table.show();
                    this.currentStates.delete(this.states.firstLoading);
                    this.currentStates.delete(this.states.loading);
                    this.prevTable.disabled = this.response.items.first;
                    this.nextTable.disabled = this.response.items.last;
                    subscribe.unsubscribe();
                }
            );
    }

    delete(flow: FlowsResponse.Flow) {
        this.deletedFlows.push(flow);
        const subscribe: Subscription = this.flowsService.flow
            .delete(flow.id)
            .subscribe(
                () => {
                    // this.updateTable(0);
                },
                () => {},
                () => {
                    subscribe.unsubscribe();
                }
            );
    }

    next() {
        this.table.wait();
        this.prevTable.disabled = true;
        this.nextTable.disabled = true;
        this.updateTable(this.response.items.number + 1);
    }

    prev() {
        this.table.wait();
        this.prevTable.disabled = true;
        this.nextTable.disabled = true;
        this.updateTable(this.response.items.number - 1);
    }

}