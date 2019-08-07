import { Injectable } from '@angular/core';
import { Observable, BehaviorSubject, Subscription } from 'rxjs';
import { Settings } from './Settings';

export enum enumOfLanguages {
    RU = 'RU',
        EN = 'EN'
}

export const setOfLanguages: Set < enumOfLanguages > = new Set([
    enumOfLanguages.EN,
    enumOfLanguages.RU,
]);


@Injectable({
    providedIn: 'root'
})
export class SettingsService {
    private settings: Settings;
    private defaultSettings: Settings = {
        darkTheme: false,
        openSide: true,
        language: enumOfLanguages.EN
    };

    settingsObserver: BehaviorSubject < any > ;
    languageObserver: BehaviorSubject < any > ;

    constructor() {
        this.settings = Object.assign({},
            this.defaultSettings,
            JSON.parse(localStorage.getItem('settingsService'))
        );
        this.settingsObserver = new BehaviorSubject < any > (this.settings);
        this.languageObserver = new BehaviorSubject < string > (this.settings.language);
        this.updateTheme();
    }

    getSettings() {
        return Observable.create(observer => {
            this.settings =
                JSON.parse(localStorage.getItem('settingsService')) ||
                Object.assign({}, this.defaultSettings);
            observer.next(this.settings);
        });
    }

    private setSettings() {
        localStorage.setItem('settingsService', JSON.stringify(this.settings));
        this.updateTheme();
    }

    private setParam(key: string, value: boolean | string) {
        this.settings[key] = value;
        this.setSettings();
    }

    private updateTheme() {
        this.getSettings().subscribe((data) => {
            let body = document.querySelector('body');
            body.classList.remove('dark-theme');
            body.classList.remove('light-theme');
            data.darkTheme ?
                body.classList.add('dark-theme') :
                body.classList.add('light-theme');
        });
    }

    private setSettingsToLocalStore(): void {
        localStorage.setItem('settingsService', JSON.stringify(this.settings));
    }

    private getSettingsFromLocalStore(): Settings {
        this.settings = Object.assign({},
            this.defaultSettings,
            (JSON.parse(localStorage.getItem('settingsService')) || {}));
        return this.settings;
    }

    setDarkTheme() {
        this.setParam('darkTheme', true);
    }

    setLightTheme() {
        this.setParam('darkTheme', false);

    }

    hideSide() {
        this.setParam('openSide', false);
    }

    showSide() {
        this.setParam('openSide', true);
    }

    setLanguage(value: string) {
        if (setOfLanguages.has(value.trim() as enumOfLanguages)) {
            value = value.trim();
        } else {
            value = enumOfLanguages.EN;
        }
        this.settings.language = value;
        this.setParam('language', value);
        this.languageObserver.next(this.settings.language);
    }
}