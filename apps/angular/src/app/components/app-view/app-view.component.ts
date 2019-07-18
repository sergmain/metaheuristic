import {
    Component,
    OnInit,
    ViewChild
} from '@angular/core';
import {
    MatSidenav
} from '@angular/material';
import {
    ActivatedRoute
} from '@angular/router';
import {
    AuthenticationService
} from '@app/services/authentication/authentication.service';
import {
    SettingsService
} from '@app/services/settings/settings.service';
import {
    Subscription
} from 'rxjs';
@Component({
    selector: 'app-view',
    templateUrl: './app-view.component.pug',
    styleUrls: ['./app-view.component.scss']
})

export class AppViewComponent implements OnInit {
    sidenavIsOpen: boolean = false;
    sidenavIsDisabled: boolean = false;
    themeActive: boolean = false;
    @ViewChild(MatSidenav) sidenav: MatSidenav;

    constructor(
        private authenticationService: AuthenticationService,
        private settingsService: SettingsService,
        private route: ActivatedRoute
    ) {}

    ngOnInit() {
        this.settingsService.getSettings().subscribe(data => {
            this.themeActive = data.darkTheme;
            this.sidenavIsOpen = data.openSide;
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

    logout() {
        return this.authenticationService.logout();
    }
}