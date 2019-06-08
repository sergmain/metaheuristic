import {
    Injectable
} from '@angular/core';
import {
    Observable,
    of
} from 'rxjs';

@Injectable({
    providedIn: 'root'
})
export class SettingsService {
    private settings
    private defaultSettings = {
        darkTheme: false,
        openSide: true
    }

    constructor() {
        this.updateTheme()
    }

    getSettings() {
        return Observable.create(observer => {
            this.settings =
                JSON.parse(localStorage.getItem('settingsService')) ||
                Object.assign({}, this.defaultSettings)
            observer.next(this.settings)
            observer.complete()
        })
    }

    setSettings() {
        localStorage.setItem('settingsService', JSON.stringify(this.settings))
        this.updateTheme()
    }

    private setParam(key: string, value: boolean) {
        this.settings[key] = value
        this.setSettings()
    }

    private updateTheme() {
        this.getSettings().subscribe((data) => {
            let body = document.querySelector('body')
            body.classList.remove('dark-theme')
            body.classList.remove('light-theme')
            data.darkTheme ?
                body.classList.add('dark-theme') :
                body.classList.add('light-theme')
        })
    }

    setDarkTheme() {
        this.setParam('darkTheme', true)
    }

    setLightTheme() {
        this.setParam('darkTheme', false)

    }

    hideSide() {
        this.setParam('openSide', false)

    }

    showSide() {
        this.setParam('openSide', true)
    }

}