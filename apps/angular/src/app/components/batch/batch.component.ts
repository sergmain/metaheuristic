import { Component, OnInit, ViewChild } from '@angular/core';
import { MatButton, MatDialog, MatTableDataSource } from '@angular/material';
import { ConfirmationDialogMethod } from '@app/components/app-dialog-confirmation/app-dialog-confirmation.component';
import { CtTableComponent } from '@app/components/ct-table/ct-table.component';
import { LoadStates } from '@app/enums/LoadStates';
import { Batch, batches, BatchService } from '@app/services/batch/batch.service';
import { Subscription } from 'rxjs';

@Component({
    selector: 'batch',
    templateUrl: './batch.component.pug',
    styleUrls: ['./batch.component.scss']
})

//  TODO: enum of execState

export class BatchComponent implements OnInit {
    readonly states = LoadStates;
    currentStates = new Set();

    response: batches.get.Response;
    dataSource = new MatTableDataSource < Batch > ([]);
    columnsToDisplay = ['id', 'createdOn', 'isBatchConsistent', 'planCode', 'execState', 'bts'];
    deletedRows: Batch[] = [];

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
                (response: batches.get.Response) => {
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

    @ConfirmationDialogMethod({
        question: (batch: Batch): string => {
            return `Do you want to delete Bacth #${batch.batch.id}`;
        },
        rejectTitle: 'Cancel',
        resolveTitle: 'Delete'
    })
    delete(batch: Batch) {
        console.log(batch)
        // const subscribe = this.batchService.batch.delete()
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