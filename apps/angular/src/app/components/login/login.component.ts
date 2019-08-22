import { Component, OnInit } from '@angular/core';
import { AuthenticationService } from '@app/services/authentication/authentication.service';
import { FormControl, FormGroup, Validators } from '@angular/forms';

@Component({
    selector: 'login-view',
    templateUrl: './login.component.pug',
    styleUrls: ['./login.component.scss']
})
export class LoginComponent implements OnInit {

    username: string = '';
    password: string = '';

    form = new FormGroup({
        username: new FormControl('', [Validators.required, Validators.minLength(3)]),
        password: new FormControl('', [Validators.required, Validators.minLength(3)]),
        remember: new FormControl(),
    });

    constructor(
        private authenticationService: AuthenticationService
    ) {}

    ngOnInit() {}

    login() {
        this.authenticationService.login(this.form.value.username, this.form.value.password, this.form.value.remember);
    }
}