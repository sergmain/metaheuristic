import { Injectable } from '@angular/core';
import { Observable, of } from 'rxjs';

export interface Account {
    id: string,
    isEnabled: string;
    login: string;
    publicName: string;
    createdOn: string;
}

function rand(min, max) {
    return Math.floor(Math.random() * (max - min)) + min;
}

function initItem(i: number) {
    return {
        id: 'id' + i,
        isEnabled: ['Yes', 'No'][rand(0, 2)],
        login: 'login ' + rand(1111, 9999),
        publicName: 'public name ' + rand(1111, 9999),
        createdOn: rand(1, 32) + '.' + rand(1, 13) + '.2018',
    }
}

function initItems(): Account[] {
    return Array.from(Array(99)).map((el, i) => initItem(i))
}

@Injectable({
    providedIn: 'root'
})

export class AccountsService {
    private data: Account[] = initItems();

    constructor() { }

    getAccounts(): Account[] {
        return this.data
    }

    getById(id: string): Account {
        return this.data.find(account => account.id === id)
    }

}