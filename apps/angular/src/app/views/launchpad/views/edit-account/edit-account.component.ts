import { Component, OnInit } from '@angular/core';
import { Location } from '@angular/common';

import { ActivatedRoute } from '@angular/router';
import { Account, AccountsService } from '@app/services/accounts/accounts.service'

@Component({
    selector: 'edit-account',
    templateUrl: './edit-account.component.html',
    styleUrls: ['./edit-account.component.scss']
})

export class EditAccountComponent implements OnInit {

    account: Account

    constructor(
        private route: ActivatedRoute,
        private accountsService: AccountsService,
        private location: Location
    ) { }

    ngOnInit() {
        this.getAccount()
    };

    getAccount(): void {
        const id = this.route.snapshot.paramMap.get('id');
        this.account = this.accountsService.getById(id)
    };

    save() {

    }

    cancel() {
        this.location.back()
    };

}
