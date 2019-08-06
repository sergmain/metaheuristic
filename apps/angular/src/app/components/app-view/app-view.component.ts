import { Component, OnInit, ViewChild } from '@angular/core';
import { MatSidenav, MatSelect } from '@angular/material';
import { ActivatedRoute } from '@angular/router';
import { AuthenticationService } from '@app/services/authentication/authentication.service';
import { SettingsService, enumOfLanguages, setOfLanguages } from '@app/services/settings/settings.service';

@Component({
    selector: 'app-view',
    templateUrl: './app-view.component.pug',
    styleUrls: ['./app-view.component.scss']
})

export class AppViewComponent implements OnInit {
    sidenavIsOpen: boolean = false;
    sidenavIsDisabled: boolean = false;
    themeActive: boolean = false;
    language: any;
    languageSelected: string;

    @ViewChild('matSlideToggleTheme') matSlideToggleTheme: MatSidenav;
    @ViewChild('matSelectLanguage') matSelectLanguage: MatSelect;

    constructor(
        private authenticationService: AuthenticationService,
        private settingsService: SettingsService,
        private route: ActivatedRoute
    ) {}

    ngOnInit() {
        this.settingsService.getSettings().subscribe(data => {
            this.themeActive = data.darkTheme;
            this.sidenavIsOpen = data.openSide;
            this.language = {
                active: data.language,
                list: setOfLanguages
            };
        });
        this.checkSidenav();
        this.isAuth();
    }

    isAuth() {
        return this.authenticationService.isAuth();
    }

    checkSidenav() {
        this.route.data.subscribe(data => {
            this.sidenavIsDisabled = data.sidenavIsDisabled || false;
            this.sidenavIsDisabled ?
                this.sidenavIsOpen = false :
                this.sidenavIsOpen = this.sidenavIsOpen;
        });
    }

    sideToggle() {
        this.matSlideToggleTheme.toggle();
        this.matSlideToggleTheme.opened ?
            this.settingsService.showSide() :
            this.settingsService.hideSide();
    }

    themeToggle(event) {
        event.checked ?
            this.settingsService.setDarkTheme() :
            this.settingsService.setLightTheme();
    }

    languageChanged() {
        this.settingsService.setLanguage(this.matSelectLanguage.value);
    }

    logout() {
        return this.authenticationService.logout();
    }
}