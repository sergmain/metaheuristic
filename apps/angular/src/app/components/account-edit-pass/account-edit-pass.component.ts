import { Component, OnInit } from '@angular/core';
import { Location } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { AccountsService } from '@app/services/accounts/accounts.service';
import { LoadStates } from '@app/enums/LoadStates';
import { AccountResponse } from '@app/models/AccountResponse';
import { FormControl, FormGroup, Validators } from '@angular/forms';
import { Subscription } from 'rxjs';

@Component({
    selector: 'account-edit-pass',
    templateUrl: './account-edit-pass.component.pug',
    styleUrls: ['./account-edit-pass.component.scss']
})

export class AccountEditPassComponent implements OnInit {
    readonly states = LoadStates;
    currentStates = new Set();
    response: AccountResponse.Response;
    account: AccountResponse.Account;

    form = new FormGroup({
        password: new FormControl('', [
            Validators.required,
            Validators.minLength(3)
        ]),
        password2: new FormControl('', [
            Validators.required,
            Validators.minLength(3),
            (control: FormControl): any => {
                const group: FormGroup = this.form;
                if (group) {
                    return (group.value.password === control.value) ? null : {
                        notSame: true
                    };
                }
                return null;
            }
        ]),
    });

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

    back(){
        this.location.back();
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
        const subscribe: Subscription = this.accountsService.account
            .passwordEditCommit(Object.assign({}, { id: this.account.id }, this.form.value))
            .subscribe(
                (response: any) => {
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