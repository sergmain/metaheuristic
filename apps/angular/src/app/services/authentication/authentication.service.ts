import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { environment } from '@src/environments/environment';
import { Subscription, from } from 'rxjs';
import { user as userResponse } from './response';
import { User } from './User';
import { SettingsService } from '@services/settings/settings.service';

@Injectable({
    providedIn: 'root'
})

export class AuthenticationService {
    constructor(
        private http: HttpClient,
        private settingsService: SettingsService
    ) {}

    isAuth() {
        if (localStorage.getItem('user')) {
            return true;
        }
        return false;
    }

    getUserRole() {
        const user = new User(JSON.parse(localStorage.getItem('user')));
        return user.getRoleSet();
    }

    login(username: string, password: string, remember: boolean) {
        const url: string = environment.baseUrl + 'user';
        const token: string = 'Basic ' + btoa(username + ':' + password);
        const headers: HttpHeaders = new HttpHeaders({ Authorization: token });

        const subscribe: Subscription = this.http.post(url, { username, password }, { headers })
            .subscribe(
                (response: userResponse.get.Response) => {
                    if (response.username) {
                        localStorage.setItem('user', JSON.stringify(Object.assign({}, response, { token })));
                    }
                },
                () => {},
                () => subscribe.unsubscribe()
            );
    }

    logout() {
        localStorage.clear();
    }
}