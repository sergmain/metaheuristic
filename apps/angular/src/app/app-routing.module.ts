import { NgModule } from '@angular/core';
import { Routes, RouterModule } from '@angular/router';
import { AuthGuard } from '@app/guards/auth/auth.guard';
import { AppComponent } from './app.component';

import { AppViewComponent } from './views/app-view/app-view.component';

import {
    NavLaunchpadComponent,
    LaunchpadComponent,

    AccountsComponent,
    AddAccountComponent,
    EditAccountComponent,
    EditPassAccountComponent,

    PlansComponent,
    AddPlanComponent,
    EditPlanComponent,

    WorkbooksComponent,
    AddWorkbookComponent,
    EditWorkbookComponent,

    ResourcesComponent,
    AddResourceComponent,

    SnippetsComponent,
    AddSnippetComponent,

    StationsComponent,
    EditStationComponent,

    ExperimentsComponent,
    AddExperimentComponent,
    EditExperimentComponent,
    InfoExperimentComponent,
    ProgressExperimentComponent,

    BatchComponent,

    AtlasComponent
} from './views/launchpad';

import {
    PilotComponent,
    NavPilotComponent,
    ProcessResourcesComponent
} from './views/pilot';

import { AboutComponent } from './views/about/about.component';

export function launchpadRoute(p): any {
    return {
        path: p.path,
        canActivate: [AuthGuard],
        component: p.component || AppViewComponent,
        children: [
            { path: '', component: p.nav || NavLaunchpadComponent, outlet: 'nav' },
            { path: '', component: p.body || LaunchpadComponent, outlet: 'body' }
        ]
    };
}

export function pilotRoute(p): any {
    return {
        path: p.path,
        canActivate: [AuthGuard],
        component: p.component || AppViewComponent,
        children: [
            { path: '', component: p.nav || NavPilotComponent, outlet: 'nav' },
            { path: '', component: p.body || PilotComponent, outlet: 'body' }
        ]
    };
}

const routes: Routes = [

    {
        path: '',
        component: AppViewComponent,
        data: { sidenavIsDisabled: true },
        children: [
            // { path: '', component: NavLaunchpadComponent, outlet: 'nav' },
            { path: '', component: AboutComponent, outlet: 'body' }
        ]
    },

    launchpadRoute({ path: 'launchpad', body: LaunchpadComponent }),
    launchpadRoute({ path: 'launchpad/plans', body: PlansComponent }),
    launchpadRoute({ path: 'launchpad/plans/add', body: AddPlanComponent }),
    launchpadRoute({ path: 'launchpad/plans/:planId/edit', body: EditPlanComponent }),
    launchpadRoute({ path: 'launchpad/plans/:planId/workbooks', body: WorkbooksComponent }),
    launchpadRoute({ path: 'launchpad/plans/:planId/workbooks/add', body: AddWorkbookComponent }),
    launchpadRoute({ path: 'launchpad/plans/:planId/workbooks/:workbookId/edit', body: AddWorkbookComponent }),

    launchpadRoute({ path: 'launchpad/experiments', body: ExperimentsComponent }),
    launchpadRoute({ path: 'launchpad/experiments/add', body: AddExperimentComponent }),
    launchpadRoute({ path: 'launchpad/experiments/:experimentId/edit', body: EditExperimentComponent }),
    launchpadRoute({ path: 'launchpad/experiments/:experimentId/info', body: InfoExperimentComponent }),
    launchpadRoute({ path: 'launchpad/experiments/:experimentId/feature-progress/:featureId', body: ProgressExperimentComponent }),

    launchpadRoute({ path: 'launchpad/resources', body: ResourcesComponent }),
    launchpadRoute({ path: 'launchpad/resources/add', body: AddResourceComponent }),
    launchpadRoute({ path: 'launchpad/resources/:id', body: ResourcesComponent }),

    launchpadRoute({ path: 'launchpad/snippets', body: SnippetsComponent }),
    launchpadRoute({ path: 'launchpad/snippets/add', body: AddSnippetComponent }),


    launchpadRoute({ path: 'launchpad/stations', body: StationsComponent }),
    launchpadRoute({ path: 'launchpad/stations/:id/edit', body: EditStationComponent }),

    launchpadRoute({ path: 'launchpad/accounts', body: AccountsComponent }),

    launchpadRoute({ path: 'launchpad/accounts/add', body: AddAccountComponent }),
    launchpadRoute({ path: 'launchpad/accounts/edit/:id', body: EditAccountComponent }),
    launchpadRoute({ path: 'launchpad/accounts/edit-password/:id', body: EditPassAccountComponent }),

    launchpadRoute({ path: 'launchpad/batch', body: BatchComponent }),

    launchpadRoute({ path: 'launchpad/atlas', body: AtlasComponent }),
    // 
    // 
    // 
    {
        path: 'pilot',
        component: AppViewComponent,

        children: [
            { path: '', component: NavPilotComponent, outlet: 'nav' },
            { path: '', component: PilotComponent, outlet: 'body' }
        ]
    },
    pilotRoute({ path: 'pilot/process-resources', body: ProcessResourcesComponent }),
    // 
    // 
    // 
    {
        path: 'about',
        component: AppViewComponent,
        data: { sidenavIsDisabled: true },
        children: [
            { path: '', component: AboutComponent, outlet: 'body' }
        ]
    },
    // 
    // 
    // 
    {
        path: '**',
        redirectTo: ''
    }
];

@NgModule({
    imports: [RouterModule.forRoot(routes)],
    exports: [RouterModule]
})

export class AppRoutingModule {}