import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable, of } from 'rxjs';
import { Router, ActivatedRoute } from '@angular/router';
import { environment } from 'environments/environment';

@Injectable({
    providedIn: 'root'
})

export class AuthenticationService {

    constructor(
        private http: HttpClient
    ) {}

    isAuth() {
        if (localStorage.getItem('user')) {
            return true;
        }
        return false;
    }


    login(username: string, password: string) {
        const url = environment.baseUrl + 'user';
        const headers = new HttpHeaders({
            'Authorization': 'Basic ' + btoa(username + ':' + password)
        });

        this.http.post < Observable < any >> (
                url, {
                    username,
                    password
                }, {
                    headers
                })
            .subscribe(user => {
                if (user) {
                    localStorage.setItem('user', JSON.stringify(Object.assign({}, user, {
                        token: 'Basic ' + btoa(username + ':' + password)
                    })));
                }
                return user;
            });
    }

    logout() {
        localStorage.removeItem('user');
    }

}