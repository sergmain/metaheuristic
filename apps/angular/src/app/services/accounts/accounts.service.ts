import { Injectable } from '@angular/core';
import { Subscription, Observable } from 'rxjs';
import { urls } from './urls';
import { HttpClient } from '@angular/common/http';

@Injectable({
    providedIn: 'root'
})

export class AccountsService {
    constructor(
        private http: HttpClient
    ) {}

    accounts = {
        get: (page: number): Observable < object > => this.http.get(urls.accounts.get(page))
    };

    account = {
        get: (id: string | number): Observable < object > => this.http.get(urls.account.get(id)),
        addCommit: (data: object): Observable < object > => this.http.post(urls.account.addCommit(data), data),
        editCommit: (data: object): Observable < object > => this.http.post(urls.account.editCommit(data), data),
        passwordEditCommit: (data: object): Observable < object > => this.http.post(urls.account.passwordEditCommit(data), data)
    };

}