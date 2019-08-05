import { Component, OnInit, ViewChild } from '@angular/core';
import { MatButton, MatDialog, MatTableDataSource } from '@angular/material';
import { ConfirmationDialogMethod } from '@app/components/app-dialog-confirmation/app-dialog-confirmation.component';
import { CtTableComponent } from '@src/app/ct/ct-table/ct-table.component';
import { LoadStates } from '@app/enums/LoadStates';
import { Batch, batches, BatchService } from '@app/services/batch/batch.service';
import { Subscription, BehaviorSubject } from 'rxjs';
import { AuthenticationService } from '@src/app/services/authentication/authentication.service';
import { TranslateService } from '@ngx-translate/core';
import { marker } from '@biesbjerg/ngx-translate-extract-marker';
import { SettingsService } from '@services/settings/settings.service';

@Component({
    selector: 'batch',
    templateUrl: './batch.component.pug',
    styleUrls: ['./batch.component.scss']
})

export class BatchComponent implements OnInit {
    states = LoadStates;
    currentStates = new Set();

    response: batches.get.Response;
    dataSource = new MatTableDataSource < Batch > ([]);
    columnsToDisplay = ['id', 'createdOn', 'isBatchConsistent', 'planCode', 'execState', 'bts'];
    deletedRows: Batch[] = [];

    @ViewChild('nextTable') nextTable: MatButton;
    @ViewChild('prevTable') prevTable: MatButton;
    @ViewChild('table') table: CtTableComponent;

    constructor(
        private translate: TranslateService,
        private dialog: MatDialog,
        private batchService: BatchService,
        private authenticationService: AuthenticationService,
        private settingsService: SettingsService
    ) {
        this.settingsService.languageObserver.subscribe((lang: string) => this.translate.use(lang));

    }

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
            return `${marker('batch.delete-dialog.Do you want to delete Batch')} #${batch.batch.id}`;
        },
        rejectTitle: `${marker('batch.delete-dialog.Cancel')}`,
        resolveTitle: `${marker('batch.delete-dialog.Delete')}`,
    })
    delete(batch: Batch) {
        const subscribe: Subscription = this.batchService.batch
            .delete(batch.batch.id)
            .subscribe(
                (response: any) => {},
                () => {},
                () => subscribe.unsubscribe()
            );
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