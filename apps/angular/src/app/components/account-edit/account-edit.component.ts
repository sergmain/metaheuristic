import { Component, OnInit } from '@angular/core';
import { Location } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { AccountsService } from '@app/services/accounts/accounts.service';
import { LoadStates } from '@app/enums/LoadStates';
import { AccountResponse } from '@app/models/AccountResponse';

@Component({
    // tslint:disable-next-line: component-selector
    selector: 'account-edit',
    templateUrl: './account-edit.component.pug',
    styleUrls: ['./account-edit.component.scss']
})

export class AccountEditComponent implements OnInit {
    readonly states = LoadStates;
    currentStates = new Set();
    response: AccountResponse.Response;
    account: AccountResponse.Account;

    constructor(
        private route: ActivatedRoute,
        private router: Router,
        private accountsService: AccountsService,
        private location: Location
    ) {}

    ngOnInit() {
        this.currentStates.add(this.states.firstLoading);
        this.getAccount();
    }


    getAccount(): void {
        const id = this.route.snapshot.paramMap.get('id');
        const subscribe = this.accountsService.account.get(id)
            .subscribe(
                (response: AccountResponse.Response) => {
                    this.account = response.account;
                },
                () => {},
                () => {
                    this.currentStates.delete(this.states.firstLoading);
                    subscribe.unsubscribe();
                }
            );
    }

    save() {
        this.currentStates.add(this.states.wait)
        const subscribe = this.accountsService.account
            .editCommit({
                id: this.account.id,
                publicName: this.account.publicName,
                enabled: this.account.enabled,
            })
            .subscribe(
                (response) => {
                    this.router.navigate(['/launchpad', 'accounts']);
                },
                () => {},
                () => {
                    this.currentStates.delete(this.states.wait)
                    subscribe.unsubscribe()
                }
            )
    }
}