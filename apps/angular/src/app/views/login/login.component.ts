import { Component, OnInit } from '@angular/core';
import { AuthenticationService } from '@app/services/authentication/authentication.service';

@Component({
    // tslint:disable-next-line: component-selector
    selector: 'login-view',
    templateUrl: './login.component.html',
    styleUrls: ['./login.component.scss']
})
export class LoginComponent implements OnInit {


    username: string = '';
    password: string = '';

    constructor(
        private authenticationService: AuthenticationService
    ) {}

    ngOnInit() {}

    login() {
        this.authenticationService.login(this.username, this.password);
    }

}