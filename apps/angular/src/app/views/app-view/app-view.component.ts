import {
    Component,
    OnInit,
    DoCheck,
    ViewChild,
    Input,
    SimpleChanges
} from '@angular/core';

import { AuthService, AuthState, ThemeState } from '@app/services/auth/auth.service'
import { ActivatedRoute } from '@angular/router'

@Component({
    selector: 'app-view',
    templateUrl: './app-view.component.html',
    styleUrls: ['./app-view.component.scss']
})

export class AppViewComponent implements OnInit, DoCheck {
    authState: AuthState
    themeState: ThemeState
    sidenavOpen: boolean = true
    constructor(
        private authService: AuthService,
        private route: ActivatedRoute
    ) {}

    ngOnInit() {
        this.obsThemeState()
        this.obsAuthState()
        this.route.data.subscribe(v => this.sidenavOpen = v.sidenavOpen === false ? false : true)
        console.log(this.sidenavOpen)
    }


    ngDoCheck() {
        this.toggle()
    }

    obsAuthState() {
        this.authService.getAuthState()
            .subscribe(authState => {
                this.authState = authState
            });
    }

    obsThemeState() {
        this.authService.getThemeState()
            .subscribe(themeState => {
                this.themeState = themeState
            })
    }

    change() {
        this.authService.toggleTheme()
    }

    toggle() {
        if (!this.themeState.dark) {
            document.body.classList.remove('dark-theme')
            document.body.classList.add('light-theme')
        } else {
            document.body.classList.remove('light-theme')
            document.body.classList.add('dark-theme')
        }
    }
    ngOnDestroy() {
        // TODO: unscribe
    }
}