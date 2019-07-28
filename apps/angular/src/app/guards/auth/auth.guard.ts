import { Injectable } from '@angular/core';
import { ActivatedRouteSnapshot, CanActivate, Router, RouterStateSnapshot } from '@angular/router';

@Injectable({
    providedIn: 'root'
})
export class AuthGuard implements CanActivate {
    constructor(private router: Router) {}

    canActivate(route: ActivatedRouteSnapshot, state: RouterStateSnapshot) {

        return true;

        // if (route.data.section === 'plans') {
        //     this.router.navigate(['/']);
        //     return false;
        // } else {
        //     return true;
        // }

    }
}