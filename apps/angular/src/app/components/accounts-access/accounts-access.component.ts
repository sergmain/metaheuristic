import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { account, Account, AccountsService, Authority } from '@app/services/accounts';
import { Role } from '@app/services/authentication';
import { Subscription } from 'rxjs';


@Component({
    selector: 'accounts-access',
    templateUrl: './accounts-access.component.pug',
    styleUrls: ['./accounts-access.component.scss']
})
export class AccountsAccessComponent implements OnInit {
    account: Account;
    response: account.get.Response;

    isManager: boolean = false;
    isOperator: boolean = false;
    isBilling: boolean = false;
    isData: boolean = false;
    isAdmin: boolean = false;
    isServerRestAccess: boolean = false;
    
    constructor(
        private router: Router,
        private route: ActivatedRoute,
        private accountsService: AccountsService
    ) {}

    ngOnInit() {
        const subscribe: Subscription = this.accountsService.account
            .get(this.route.snapshot.paramMap.get('accountId'))
            .subscribe(
                (response: account.get.Response) => {
                    this.response = response;
                    this.account = response.account;
                    this.account.authorities.forEach((authority: Authority) => {
                        if (authority.authority === Role.Manager) { this.isManager = true; }
                        if (authority.authority === Role.Operator) { this.isOperator = true; }
                        if (authority.authority === Role.Billing) { this.isBilling = true; }
                        if (authority.authority === Role.Data) { this.isData = true; }
                        if (authority.authority === Role.Admin) { this.isAdmin = true; }
                        if (authority.authority === Role.ServerRestAccess) { this.isServerRestAccess = true; }

                    });
                },
                () => {},
                () => subscribe.unsubscribe()
            );
    }

    save() {
        const formData: FormData = new FormData();
        const roles: string[] = [];
        const accountId: string = this.route.snapshot.paramMap.get('accountId');

        if (this.isAdmin) { roles.push(Role.Admin); }
        if (this.isBilling) { roles.push(Role.Billing); }
        if (this.isData) { roles.push(Role.Data); }
        if (this.isManager) { roles.push(Role.Manager); }
        if (this.isOperator) { roles.push(Role.Operator); }

        formData.append('accountId', this.route.snapshot.paramMap.get('accountId'));
        formData.append('roles', roles.join(','));

        const subscribe: Subscription = this.accountsService.account
            .roleCommit({ accountId, roles: roles.join(',') })
            .subscribe(
                (response: any) => {
                    console.log(response);
                },
                () => {},
                () => {
                    subscribe.unsubscribe();
                }
            );
    }

    back() {
        this.router.navigate(['/launchpad', 'accounts']);
    }
}