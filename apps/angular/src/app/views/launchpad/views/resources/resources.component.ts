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
    ResourcesService
} from '@app/services/resources/resources.service';
import {
    ResourcesResponse
} from '@app/models';

import {
    LoadStates
} from '@app/enums/LoadStates';
import {
    CtTableComponent
} from '@app/custom-tags/ct-table/ct-table.component';
import {
    Subscription
} from 'rxjs';

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
    response: ResourcesResponse.Response;
    deletedResourses: (ResourcesResponse.Resource)[] = [];
    dataSource = new MatTableDataSource < ResourcesResponse.Resource > ([]);
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
                (response: ResourcesResponse.Response) => {
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

    delete(el: ResourcesResponse.Resource) {
        this.deletedResourses.push(el);
        const subscribe: Subscription = this.resourcesService.resource.delete(el.id)
            .subscribe(() => {
                // this.updateTable(0);
                subscribe.unsubscribe();
            });
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