import { Component, OnInit, ViewChild } from '@angular/core';
import { MatButton, MatDialog, MatTableDataSource } from '@angular/material';
import { CtTableComponent } from '@app/components/ct-table/ct-table.component';
import { LoadStates } from '@app/enums/LoadStates';
import { PlansResponse } from '@app/models';
import { PlansService } from '@app/services/plans/plans.service';
import { ConfirmationDialogMethod } from '@app/components/app-dialog-confirmation/app-dialog-confirmation.component';

import { Subscription } from 'rxjs';

@Component({
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
    columnsToDisplay = ['id', 'code', 'createdOn', 'valid', 'bts'];
    deletedPlans: PlansResponse.Plan[] = [];
    archivedPlans: PlansResponse.Plan[] = [];

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

    @ConfirmationDialogMethod({
        question: (plan: PlansResponse.Plan): string =>
            `Do you want to delete Plan\xa0#${plan.id}`,
        rejectTitle: 'Cancel',
        resolveTitle: 'Delete'
    })
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

    @ConfirmationDialogMethod({
        question: (plan: PlansResponse.Plan): string =>
            `Do you want to archive Plan\xa0#${plan.id}`,
        rejectTitle: 'Cancel',
        resolveTitle: 'Archive'
    })

    archive(plan: PlansResponse.Plan) {
        this.archivedPlans.push(plan);
        const subscribe: Subscription = this.planService.plan
            .archive(plan.id)
            .subscribe(
                () => {},
                () => {},
                () => {
                    subscribe.unsubscribe();
                },
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