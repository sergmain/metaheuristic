import { Component, OnInit, ViewChild } from '@angular/core';
import { MatButton, MatDialog, MatTabGroup, MatTableDataSource } from '@angular/material';
import { ConfirmationDialogInterface, ConfirmationDialogMethod } from '@app/components/app-dialog-confirmation/app-dialog-confirmation.component';
import { LoadStates } from '@app/enums/LoadStates';
import { PlansResponse } from '@app/models';
import { PlansService } from '@app/services/plans/plans.service';
import { PlansArchiveComponent } from '@src/app/components/plans-archive/plans-archive.component';
import { CtTableComponent } from '@src/app/ct/ct-table/ct-table.component';
import { Subscription } from 'rxjs';

@Component({
    selector: 'plans-view',
    templateUrl: './plans.component.pug',
    styleUrls: ['./plans.component.scss']
})

export class PlansComponent implements OnInit, ConfirmationDialogInterface {
    TABINDEX: number = 0;

    states = LoadStates;
    currentStates = new Set();
    response: PlansResponse.Response;
    dataSource = new MatTableDataSource < PlansResponse.Plan > ([]);
    columnsToDisplay = ['id', 'code', 'createdOn', 'valid', 'bts'];
    deletedPlans: PlansResponse.Plan[] = [];
    archivedPlans: PlansResponse.Plan[] = [];

    @ViewChild('nextTable') nextTable: MatButton;
    @ViewChild('prevTable') prevTable: MatButton;
    @ViewChild('matTabGroup') matTabGroup: MatTabGroup;
    @ViewChild('table') table: CtTableComponent;
    @ViewChild('plansArchive') plansArchive: PlansArchiveComponent;

    constructor(
        readonly dialog: MatDialog,
        private planService: PlansService,
    ) {
        this.currentStates.add(this.states.firstLoading);
        this.updateTable(0);
    }

    ngOnInit() {}

    updateTable(page: number) {
        this.currentStates.add(this.states.loading);
        const subscribe: Subscription = this.planService.plans
            .get({ page })
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

    tabChange() {
        if (this.matTabGroup.selectedIndex === 1) {
            this.plansArchive.updateTable(0);
        }
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