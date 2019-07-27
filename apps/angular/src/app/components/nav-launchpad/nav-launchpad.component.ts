import { Component, OnInit } from '@angular/core';
import { UserRoleInterface, AuthenticationService, Role } from '@src/app/services/authentication';

@Component({
    selector: 'app-nav-launchpad',
    templateUrl: './nav-launchpad.component.pug',
    styleUrls: ['./nav-launchpad.component.scss']
})
export class NavLaunchpadComponent implements OnInit {

    userRole: Set < Role > ;

    constructor(
        private authenticationService: AuthenticationService
    ) {
        this.userRole = this.authenticationService.getUserRole();
    }

    ngOnInit() {}

    accessPlans() {
        if (this.userRole.has(Role.Admin) ||
            this.userRole.has(Role.Manager) ||
            this.userRole.has(Role.Data)
        ) {
            return true;
        }
        return false;
    }

    accessBatch() {
        if (this.userRole.has(Role.Admin) ||
            this.userRole.has(Role.Manager) ||
            this.userRole.has(Role.Operator)
        ) {
            return true;
        }
        return false;
    }

    accessExperiments() {
        if (this.userRole.has(Role.Admin) ||
            this.userRole.has(Role.Manager) ||
            this.userRole.has(Role.Data)
        ) {
            return true;
        }
        return false;
    }

    accessAtlas() {
        if (this.userRole.has(Role.Admin) ||
            this.userRole.has(Role.Data)
        ) {
            return true;
        }
        return false;
    }

    accessResourses() {
        if (this.userRole.has(Role.Admin) ||
            this.userRole.has(Role.Data)
        ) {
            return true;
        }
        return false;
    }

    accessSnippets() {
        if (this.userRole.has(Role.Admin) ||
            this.userRole.has(Role.Manager) ||
            this.userRole.has(Role.Data)
        ) {
            return true;
        }
        return false;
    }

    accessStations() {
        if (this.userRole.has(Role.Admin)

        ) {
            return true;
        }
        return false;
    }

    accessAccounts() {
        if (this.userRole.has(Role.Admin)

        ) {
            return true;
        }
        return false;
    }
}