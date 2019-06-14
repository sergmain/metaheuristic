import { Component, OnInit, ViewChild, } from '@angular/core';
import { PlansService } from '@app/services/plans/plans.service';
import { MatTableDataSource, MatButton } from '@angular/material';
import { LoadStates } from '@app/enums/LoadStates';
import { PlansResponse } from '@app/models';
import { CtTableComponent } from '@app/custom-tags/ct-table/ct-table.component';
import { Subscription } from 'rxjs';
import { MatDialog } from '@angular/material';
import { ConfirmationDialogMethod } from '@app/views/app-dialog-confirmation/app-dialog-confirmation.component';

@Component({
    // tslint:disable-next-line: component-selector
    selector: 'plans-view',
    templateUrl: './plans.component.pug',
    styleUrls: ['./plans.component.scss']
})
// @ConfirmationDialogClass
export class PlansComponent implements OnInit {
    readonly states = LoadStates;
    currentStates = new Set();
    response: PlansResponse.Response;
    dataSource = new MatTableDataSource < PlansResponse.Plan > ([]);
    columnsToDisplay = ['id', 'code', 'createdOn', 'valid', 'locked', 'bts'];
    deletedPlans: (PlansResponse.Plan)[] = [];
    @ViewChild('nextTable') nextTable: MatButton;
    @ViewChild('prevTable') prevTable: MatButton;
    @ViewChild('table') table: CtTableComponent;

    constructor(
        private dialog: MatDialog,
        private planService: PlansService
    ) {}

    ngOnInit() {
        this.currentStates.add(this.states.firstLoading);
        this.updateTable(0);
    }

    updateTable(page: number) {
        this.currentStates.add(this.states.loading);
        const subscribe: Subscription = this.planService.plans.get({
                page
            })
            .subscribe(
                (response: PlansResponse.Response) => {
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

    @ConfirmationDialogMethod()
    delete(plan: PlansResponse.Plan) {
        this.deletedPlans.push(plan);
        const subscribe: Subscription = this.planService.plan
            .delete(plan.id)
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