import { Component, OnInit, ViewChild } from '@angular/core';
import { MatButton, MatDialog, MatTableDataSource } from '@angular/material';
import { ConfirmationDialogMethod } from '@app/components/app-dialog-confirmation/app-dialog-confirmation.component';
import { CtTableComponent } from '@app/components/ct-table/ct-table.component';
import { LoadStates } from '@app/enums/LoadStates';
import { Batch, batches, BatchService, batch } from '@app/services/batch/batch.service';
import { Subscription } from 'rxjs';
import { Plan } from '@app/models/Plan';


@Component({
    selector: 'batch-add',
    templateUrl: './batch-add.component.pug',
    styleUrls: ['./batch-add.component.scss']
})


export class BatchAddComponent implements OnInit {
    readonly states = LoadStates;
    currentStates = new Set();
    response: batch.add.Response;

    currentPlan: Plan;
    listOfPlans: Plan[] = [];

    constructor(
        private batchService: BatchService
    ) {
        this.currentStates.add(this.states.firstLoading);
    }

    ngOnInit() {
        this.updateResponse();
    }


    updateResponse() {
        const subscribe: Subscription = this.batchService.batch
            .add()
            .subscribe(
                (response: batch.add.Response) => {
                    this.response = response;
                    this.listOfPlans = this.response.items;
                },
                () => {},
                () => {
                    this.currentStates.delete(this.states.firstLoading);
                    this.currentStates.delete(this.states.loading);
                    this.currentStates.add(this.states.show);
                    subscribe.unsubscribe();
                }
            );
    }
}