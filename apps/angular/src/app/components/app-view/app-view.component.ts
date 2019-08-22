import { Component, OnInit, ViewChild } from '@angular/core';
import { MatSidenav, MatSelect, MatSlideToggle } from '@angular/material';
import { ActivatedRoute, Router } from '@angular/router';
import { AuthenticationService } from '@app/services/authentication/authentication.service';
import { SettingsService, enumOfLanguages, setOfLanguages } from '@app/services/settings/settings.service';
import { Subscription } from 'rxjs';

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

    @ViewChild(MatSidenav) sidenav: MatSidenav;
    @ViewChild('matSlideToggleTheme') matSlideToggleTheme: MatSlideToggle;
    @ViewChild('matSelectLanguage') matSelectLanguage: MatSelect;

    constructor(
        private authenticationService: AuthenticationService,
        private settingsService: SettingsService,
        private route: ActivatedRoute,
        private router: Router,

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
        const subscribe: Subscription = this.route.data.subscribe(
            (response: any) => {
                this.sidenavIsDisabled = response.sidenavIsDisabled || false;
                this.sidenavIsDisabled ?
                    this.sidenavIsOpen = false :
                    this.sidenavIsOpen = this.sidenavIsOpen;
            },
            () => {},
            () => subscribe.unsubscribe()
        );
    }

    sideToggle() {
        this.sidenav.toggle();
        this.sidenav.opened ?
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
        this.authenticationService.logout();
        this.router.navigate(['/']);
    }
}