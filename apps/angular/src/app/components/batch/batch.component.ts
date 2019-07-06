import { Component, OnInit, ViewChild, ViewEncapsulation } from '@angular/core';
import { BatchService } from '@app/services/batch/batch.service';
import { MatTableDataSource, MatButton } from '@angular/material';
import { LoadStates } from '@app/enums/LoadStates';
import { BatchesResponse, Plan } from '@app/models';
import { CtTableComponent } from '@app/custom-tags/ct-table/ct-table.component';
import { Subscription } from 'rxjs';
import { MatDialog } from '@angular/material';
import { ConfirmationDialogMethod } from '@app/views/app-dialog-confirmation/app-dialog-confirmation.component';

@Component({
    selector: 'batch',
    templateUrl: './batch.component.pug',
    styleUrls: ['./batch.component.scss']
})
export class BatchComponent implements OnInit {
    readonly states = LoadStates;
    currentStates = new Set();

    response: BatchesResponse.Response;
    dataSource = new MatTableDataSource < BatchesResponse.ContentEntity > ([]);
    columnsToDisplay = ['id', 'createdOn', 'isBatchConsistent', 'planCode', 'execState', 'bts'];
    deletedRows: (BatchesResponse.ContentEntity)[] = [];


    @ViewChild('nextTable') nextTable: MatButton;
    @ViewChild('prevTable') prevTable: MatButton;
    @ViewChild('table') table: CtTableComponent;

    constructor(
        private dialog: MatDialog,
        private batchService: BatchService
    ) {}

    ngOnInit() {
        this.currentStates.add(this.states.firstLoading);
        this.updateTable(0);
    }

    updateTable(page: number) {
        this.currentStates.add(this.states.loading);
        const subscribe: Subscription = this.batchService.batches.get({
                page
            })
            .subscribe(
                (response: BatchesResponse.Response) => {
                    this.response = response;
                    this.dataSource = new MatTableDataSource(response.batches.content || []);
                },
                () => {},
                () => {
                    this.table.show();
                    this.currentStates.delete(this.states.firstLoading);
                    this.currentStates.delete(this.states.loading);
                    this.prevTable.disabled = this.response.batches.first;
                    this.nextTable.disabled = this.response.batches.last;
                    subscribe.unsubscribe();
                }
            );
    }

    // @ConfirmationDialogMethod({
    //     question: (plan: Plan): string => {
    //         console.log(plan)
    //         return `Do you want to delete Plan #`;
    //     },
    //     rejectTitle: 'Cancel',
    //     resolveTitle: 'Delete'
    // })
    delete() {

    }

    next() {
        this.table.wait();
        this.prevTable.disabled = true;
        this.nextTable.disabled = true;
        this.updateTable(this.response.batches.number + 1);
    }

    prev() {
        this.table.wait();
        this.prevTable.disabled = true;
        this.nextTable.disabled = true;
        this.updateTable(this.response.batches.number - 1);
    }
}