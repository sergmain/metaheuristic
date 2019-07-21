import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable, of } from 'rxjs';
import { Router, ActivatedRoute } from '@angular/router';
import { environment } from '@src/environments/environment';

export interface AuthState {
    auth: boolean;
}

export interface ThemeState {
    dark: boolean;
}

@Injectable({
    providedIn: 'root'
})

export class AuthService {

    public authState: AuthState = {
        auth: false
    };

    public themeState: ThemeState = {
        dark: false
    };

    constructor(
        private route: ActivatedRoute,
        private router: Router,
        private http: HttpClient
    ) {}


    login(data) {

        const headers = new HttpHeaders({
            'Authorization': 'Basic ' + btoa(data.username + ':' + data.password)
        });


        let url = environment.baseUrl + 'user';
        this.http.post < Observable < boolean >> (url, {
            username: data.username,
            password: data.password
        }, { headers }).subscribe(isValid => {
            if (isValid) {
                sessionStorage.setItem('token', btoa(data.username + ':' + data.password));
                this.router.navigate(['']);
                console.log('good');

            } else {
                console.log('Authentication failed.');
            }
        });
    }


    getAuthState(): Observable < AuthState > {
        return of(this.authState);
    }

    getThemeState(): Observable < ThemeState > {
        return of(this.themeState);
    }

    change() {
        this.authState.auth = !this.authState.auth;
    }

    toggleTheme() {
        this.themeState.dark = !this.themeState.dark;
    }
}