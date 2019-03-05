import { Component, OnInit, ViewChild } from '@angular/core';
import { MatPaginator, MatTableDataSource } from '@angular/material';
import { Account, AccountsService } from '@app/services/accounts/accounts.service'

@Component({
    selector: 'accounts-view',
    templateUrl: './accounts.component.html',
    styleUrls: ['./accounts.component.scss']
})

export class AccountsComponent implements OnInit {
    dataSource = new MatTableDataSource<Account>([]);
    columnsToDisplay = ['id','isEnabled', 'login', 'publicName', 'createdOn', 'bts'];
    constructor(private accountsService: AccountsService) { }

    @ViewChild(MatPaginator) paginator: MatPaginator;

    applyFilter(filterValue: string) {
        this.dataSource.filter = filterValue.trim().toLowerCase();
    }

    ngOnInit() {
        this.dataSource = new MatTableDataSource<Account>(this.accountsService.getAccounts());
        this.dataSource.paginator = this.paginator;
    }
}