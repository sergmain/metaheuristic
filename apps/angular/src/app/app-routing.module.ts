import { NgModule } from '@angular/core';
import { Routes, RouterModule } from '@angular/router';
import { AppComponent } from './app.component';

import { AppViewComponent } from './views/app-view/app-view.component';

import {
    NavLaunchpadComponent,
    LaunchpadComponent,

    AccountsComponent,
    AddAccountComponent,
    EditAccountComponent,
    EditPassAccountComponent,

    FlowsComponent,
    AddFlowComponent,
    EditFlowComponent,

    InstancesComponent,
    AddInstanceComponent,
    EditInstanceComponent,

    ResourcesComponent,
    AddResourceComponent,

    SnippetsComponent,
    StationsComponent,

    ExperimentsComponent,
    AddExperimentComponent,
    EditExperimentComponent,
    InfoExperimentComponent,
    ProgressExperimentComponent
} from './views/launchpad';

import { PilotComponent } from './views/pilot/pilot.component';
import { AboutComponent } from './views/about/about.component';

export function launchpadRoute(p): any {
    return {
        path: p.path,
        component: p.component || AppViewComponent,
        children: [
            { path: '', component: p.nav || NavLaunchpadComponent, outlet: 'nav' },
            { path: '', component: p.body || LaunchpadComponent, outlet: 'body' }
        ]
    }
}

let routes: Routes = [

    {
        path: '',
        component: AppViewComponent,
        data: { sidenavOpen: false } ,
        children: [
            { path: '', component: NavLaunchpadComponent, outlet: 'nav'},
            { path: '', component: AboutComponent, outlet: 'body' }
        ]
    },

    launchpadRoute({ path: 'launchpad', body: LaunchpadComponent }),
    launchpadRoute({ path: 'launchpad/flows', body: FlowsComponent }),
    launchpadRoute({ path: 'launchpad/flows/add', body: AddFlowComponent }),
    launchpadRoute({ path: 'launchpad/flows/:flowId/edit', body: EditFlowComponent }),
    launchpadRoute({ path: 'launchpad/flows/:flowId/instances', body: InstancesComponent }),
    launchpadRoute({ path: 'launchpad/flows/:flowId/instances/:instanceId/add', body: LaunchpadComponent }),
    launchpadRoute({ path: 'launchpad/flows/:flowId/instances/:instanceId/edit', body: LaunchpadComponent }),

    launchpadRoute({ path: 'launchpad/experiments', body: ExperimentsComponent }),
    launchpadRoute({ path: 'launchpad/experiments/add', body: AddExperimentComponent }),
    launchpadRoute({ path: 'launchpad/experiments/:experimentId/edit', body: EditExperimentComponent }),
    launchpadRoute({ path: 'launchpad/experiments/:experimentId/info', body: InfoExperimentComponent }),
    launchpadRoute({ path: 'launchpad/experiments/:experimentId/feature-progress/:featureId', body: ProgressExperimentComponent }),




    launchpadRoute({ path: 'launchpad/resources', body: ResourcesComponent }),
    launchpadRoute({ path: 'launchpad/resources/add', body: AddResourceComponent }),
    launchpadRoute({ path: 'launchpad/resources/:id', body: ResourcesComponent }),

    launchpadRoute({ path: 'launchpad/snippets', body: SnippetsComponent }),
    launchpadRoute({ path: 'launchpad/stations', body: StationsComponent }),
    launchpadRoute({ path: 'launchpad/accounts', body: AccountsComponent }),

    launchpadRoute({ path: 'launchpad/accounts/add', body: AddAccountComponent }),
    launchpadRoute({ path: 'launchpad/accounts/edit/:id', body: EditAccountComponent }),
    launchpadRoute({ path: 'launchpad/accounts/edit-password/:id', body: EditPassAccountComponent }),

    {
        path: 'pilot',
        component: AppViewComponent,
        children: [
            { path: '', component: PilotComponent, outlet: 'nav' },
            { path: '', component: PilotComponent, outlet: 'body' }
        ]
    },

    {
        path: 'about',
        component: AppViewComponent,
        children: [
            { path: '', component: AboutComponent, outlet: 'body' }
        ]
    },
];


@NgModule({
    imports: [RouterModule.forRoot(routes)],
    exports: [RouterModule]
})

export class AppRoutingModule {}