import { Component, OnInit, ViewChild } from '@angular/core';
import { MatButton, MatTableDataSource, MatDialog } from '@angular/material';
import { CtTableComponent } from '@app/components/ct-table/ct-table.component';
import { LoadStates } from '@app/enums/LoadStates';
import { ResourcesService, resources, Resource } from '@app/services/resources/resources.service';
import { Subscription } from 'rxjs';
import { ConfirmationDialogMethod } from '@app/components/app-dialog-confirmation/app-dialog-confirmation.component';

@Component({
    // tslint:disable-next-line: component-selector
    selector: 'resources-view',
    templateUrl: './resources.component.pug',
    styleUrls: ['./resources.component.scss'],
    providers: [ResourcesService]
})

export class ResourcesComponent implements OnInit {
    readonly states = LoadStates;
    currentStates = new Set();
    response: resources.get.Response;
    deletedResourses: Resource[] = [];
    dataSource = new MatTableDataSource < Resource > ([]);
    columnsToDisplay: (string)[] = [
        'valid',
        'uploadTs',
        'dataTypeAsStr',
        'checksum',
        'code',
        'poolCode',
        'manual',
        'filename',
        'storageUrl',
        'bts'
    ];

    @ViewChild('nextTable') nextTable: MatButton;
    @ViewChild('prevTable') prevTable: MatButton;
    @ViewChild('table') table: CtTableComponent;

    constructor(
        private dialog: MatDialog,
        private resourcesService: ResourcesService
    ) {}

    ngOnInit() {
        this.currentStates.add(this.states.firstLoading);
        this.updateTable(0);
    }

    updateTable(page: number) {
        this.currentStates.add(this.states.loading);
        const subscribe: Subscription = this.resourcesService.resources.get(page)
            .subscribe(
                (response: resources.get.Response) => {
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
        question: (resource: Resource): string =>
            `Do you want to delete Resource\xa0#${resource.id}`,
        rejectTitle: 'Cancel',
        resolveTitle: 'Delete'
    })
    delete(resource: Resource) {
        this.deletedResourses.push(resource);
        const subscribe: Subscription = this.resourcesService.resource.delete(resource.id)
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