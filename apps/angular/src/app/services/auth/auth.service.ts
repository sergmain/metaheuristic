import { Injectable } from '@angular/core';
import { Observable, of } from 'rxjs';

export interface AuthState {
    auth: boolean
}

export interface ThemeState {
    dark: boolean
}

@Injectable({
    providedIn: 'root'
})

export class AuthService {
    public authState: AuthState = {
        auth: true
    }
    public themeState: ThemeState = {
        dark: false
    }

    constructor() { }

    getAuthState(): Observable<AuthState> {
        return of(this.authState);
    }

    getThemeState(): Observable<ThemeState> {
        return of(this.themeState);
    }

    change() {
        this.authState.auth = !this.authState.auth
    }

    toggleTheme() {
        this.themeState.dark = !this.themeState.dark
    }
}
