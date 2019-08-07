import { Component, OnInit } from '@angular/core';
import { FormControl, FormGroup, Validators } from '@angular/forms';
import { LoadStates } from '@app/enums/LoadStates';
import { map } from 'rxjs/operators';
import { AccountsService } from '@app/services/accounts/accounts.service';
import { Subscription } from 'rxjs';
import { Router } from '@angular/router';
import { DefaultResponse } from '@app/models/DefaultResponse';

@Component({
    selector: 'account-add',
    templateUrl: './account-add.component.pug',
    styleUrls: ['./account-add.component.scss']
})

export class AccountAddComponent implements OnInit {
    readonly states = LoadStates;
    currentStates = new Set();
    response: DefaultResponse;
    form = new FormGroup({
        username: new FormControl('', [Validators.required, Validators.minLength(3)]),
        password: new FormControl('', [Validators.required, Validators.minLength(3)]),
        password2: new FormControl('', [
            Validators.required,
            Validators.minLength(3),
            (control: FormControl) => {
                const group: FormGroup = this.form;
                if (group) {
                    return (group.value.password === control.value) ? null : {
                        notSame: true
                    };
                }
                return null;
            }
        ]),
        publicName: new FormControl('', [Validators.required, Validators.minLength(3)]),
    });

    constructor(
        private accountsService: AccountsService,
        private router: Router,
    ) {}

    ngOnInit() {}

    create() {
        this.currentStates.add(this.states.wait);
        const subscribe: Subscription = this.accountsService.account
            .addCommit(this.form.value)
            .subscribe(
                (response: DefaultResponse) => {
                    if (response.status.toLowerCase() === 'ok') {
                        this.router.navigate(['/launchpad', 'accounts']);
                    }
                },
                () => {},
                () => {
                    this.currentStates.delete(this.states.wait);
                    subscribe.unsubscribe();
                }
            );
    }
}