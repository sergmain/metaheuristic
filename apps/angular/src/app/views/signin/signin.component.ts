import { Component, OnInit } from '@angular/core';
import { AuthService } from '@app/services/auth/auth.service'
@Component({
    selector: 'signin-view',
    templateUrl: './signin.component.html',
    styleUrls: ['./signin.component.scss'],
})
export class SigninComponent implements OnInit {

    constructor(private authService: AuthService) { }

    ngOnInit() { }

    signin() {
        this.authService.change()
    }
}
