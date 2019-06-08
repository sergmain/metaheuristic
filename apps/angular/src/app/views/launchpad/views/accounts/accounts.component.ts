import { Component, OnInit, ViewChild } from '@angular/core';
import { MatTableDataSource, MatButton } from '@angular/material';
import { AccountsService } from '@app/services/accounts/accounts.service';
import { LoadStates } from '@app/enums/LoadStates';
import { CtTableComponent } from '@app/custom-tags/ct-table/ct-table.component';
import { AccountsResponse } from '@app/models/AccountsResponse';
import { Subscription } from 'rxjs';

@Component({
    // tslint:disable-next-line: component-selector
    selector: 'accounts-view',
    templateUrl: './accounts.component.pug',
    styleUrls: ['./accounts.component.scss']
})

export class AccountsComponent implements OnInit {
    readonly states = LoadStates;
    currentStates = new Set();

    dataSource = new MatTableDataSource < AccountsResponse.Accounts > ([]);
    columnsToDisplay = ['id', 'isEnabled', 'login', 'publicName', 'createdOn', 'bts'];

    accountsResponse: AccountsResponse.Response;

    @ViewChild('nextTable') nextTable: MatButton;
    @ViewChild('prevTable') prevTable: MatButton;
    @ViewChild('table') table: CtTableComponent;

    constructor(
        private accountsService: AccountsService
    ) {}

    ngOnInit() {
        this.currentStates.add(this.states.firstLoading);
        this.updateTable(0);
    }

    updateTable(page: number) {
        this.currentStates.add(this.states.loading);
        const subscribe: Subscription = this.accountsService.accounts
            .get(page)
            .subscribe(
                (response: AccountsResponse.Response) => {
                    this.accountsResponse = response;
                    this.dataSource = new MatTableDataSource(this.accountsResponse.accounts.content || []);
                    this.prevTable.disabled = this.accountsResponse.accounts.first;
                    this.nextTable.disabled = this.accountsResponse.accounts.last;
                    this.table.show();
                    this.currentStates.delete(this.states.firstLoading);
                    this.currentStates.delete(this.states.loading);
                },
                () => {},
                () => {
                    subscribe.unsubscribe();
                }
            );
    }

    next() {
        this.prevTable.disabled = true;
        this.nextTable.disabled = true;
        this.updateTable(this.accountsResponse.accounts.number + 1);
        this.table.wait();
    }

    prev() {
        this.prevTable.disabled = true;
        this.nextTable.disabled = true;
        this.updateTable(this.accountsResponse.accounts.number - 1);
        this.table.wait();
    }

}