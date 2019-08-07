import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { AuthGuard } from '@app/guards/auth/auth.guard';
import { AboutComponent, AccountAccessComponent, AccountsComponent, AccountAddComponent, ExperimentAddComponent, PlanAddComponent, ResourceAddComponent, SnippetAddComponent, AddWorkbookComponent, AppViewComponent, AtlasExperimentsComponent, AtlasExperimentExportImportComponent, AtlasExperimentFeatureProgressComponent, AtlasExperimentInfoComponent, BatchAddComponent, BatchComponent, BatchStatusComponent, BillingComponent, AccountEditComponent, ExperimentEditComponent, AccountEditPassComponent, EditPlanComponent, StationEditComponent, ExperimentsComponent, ExperimentInfoComponent, LaunchpadComponent, NavLaunchpadComponent, NavPilotComponent, PilotComponent, PlansComponent, ResourcesComponent, SnippetsComponent, StationsComponent, WorkbooksComponent, ExperimentFeatureProgressComponent } from './components';

export function launchpadRoute(p: any): any {
    return {
        path: p.path,
        canActivate: [AuthGuard],
        component: p.component || AppViewComponent,
        children: [
            { path: '', component: p.nav || NavLaunchpadComponent, outlet: 'nav' },
            { path: '', component: p.body || LaunchpadComponent, outlet: 'body' }
        ],
        data: Object.assign({}, p.data)
    };
}

export function billingRoute(p: any): any {
    return {
        path: p.path,
        canActivate: [AuthGuard],
        component: p.component || AppViewComponent,
        children: [
            { path: '', component: p.nav || NavPilotComponent, outlet: 'nav' },
            { path: '', component: p.body || PilotComponent, outlet: 'body' }
        ],
        data: Object.assign({}, p.data)

    };
}


export function pilotRoute(p: any): any {
    return {
        path: p.path,
        canActivate: [AuthGuard],
        component: p.component || AppViewComponent,
        children: [
            { path: '', component: p.nav || NavPilotComponent, outlet: 'nav' },
            { path: '', component: p.body || PilotComponent, outlet: 'body' }
        ],
        data: Object.assign({}, p.data)

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

    launchpadRoute({ path: 'launchpad/plans', body: PlansComponent, data: { section: 'plans' } }),
    launchpadRoute({ path: 'launchpad/plans/add', body: PlanAddComponent, data: { section: 'plans' } }),
    launchpadRoute({ path: 'launchpad/plans/:planId/edit', body: EditPlanComponent, data: { section: 'plans' } }),
    launchpadRoute({ path: 'launchpad/plans/:planId/workbooks', body: WorkbooksComponent, data: { section: 'plans' } }),
    launchpadRoute({ path: 'launchpad/plans/:planId/workbooks/add', body: AddWorkbookComponent, data: { section: 'plans' } }),
    launchpadRoute({ path: 'launchpad/plans/:planId/workbooks/:workbookId/edit', body: AddWorkbookComponent, data: { section: 'plans' } }),

    launchpadRoute({ path: 'launchpad/experiments', body: ExperimentsComponent, data: { section: 'experiments' } }),
    launchpadRoute({ path: 'launchpad/experiments/add', body: ExperimentAddComponent, data: { section: 'experiments' } }),
    launchpadRoute({ path: 'launchpad/experiments/:experimentId/edit', body: ExperimentEditComponent, data: { section: 'experiments' } }),
    launchpadRoute({ path: 'launchpad/experiments/:experimentId/info', body: ExperimentInfoComponent, data: { section: 'experiments' } }),
    launchpadRoute({ path: 'launchpad/experiments/:experimentId/feature-progress/:featureId', body: ExperimentFeatureProgressComponent, data: { section: 'experiments' } }),


    launchpadRoute({ path: 'launchpad/resources', body: ResourcesComponent, data: { section: 'resources' } }),
    launchpadRoute({ path: 'launchpad/resources/add', body: ResourceAddComponent, data: { section: 'resources' } }),
    launchpadRoute({ path: 'launchpad/resources/:id', body: ResourcesComponent, data: { section: 'resources' } }),

    launchpadRoute({ path: 'launchpad/snippets', body: SnippetsComponent, data: { section: 'snippets' } }),
    launchpadRoute({ path: 'launchpad/snippets/add', body: SnippetAddComponent, data: { section: 'snippets' } }),

    launchpadRoute({ path: 'launchpad/stations', body: StationsComponent, data: { section: 'stations' } }),
    launchpadRoute({ path: 'launchpad/stations/:id/edit', body: StationEditComponent, data: { section: 'stations' } }),

    launchpadRoute({ path: 'launchpad/accounts', body: AccountsComponent, data: { section: 'accounts' } }),
    launchpadRoute({ path: 'launchpad/accounts/add', body: AccountAddComponent, data: { section: 'accounts' } }),
    launchpadRoute({ path: 'launchpad/accounts/access/:accountId', body: AccountAccessComponent, data: { section: 'accounts' } }),
    launchpadRoute({ path: 'launchpad/accounts/edit/:id', body: AccountEditComponent, data: { section: 'accounts' } }),
    launchpadRoute({ path: 'launchpad/accounts/edit-password/:id', body: AccountEditPassComponent, data: { section: 'accounts' } }),

    launchpadRoute({ path: 'launchpad/batch', body: BatchComponent, data: { section: 'batch' } }),
    launchpadRoute({ path: 'launchpad/batch/:id/status', body: BatchStatusComponent, data: { section: 'batch' } }),
    launchpadRoute({ path: 'launchpad/batch/add', body: BatchAddComponent, data: { section: 'batch' } }),

    launchpadRoute({ path: 'launchpad/atlas/experiments', body: AtlasExperimentsComponent, data: { section: 'atlas' } }),
    launchpadRoute({ path: 'launchpad/atlas/experiment-export-import/:atlasId', body: AtlasExperimentExportImportComponent, data: { section: 'atlas' } }),
    launchpadRoute({ path: 'launchpad/atlas/experiment-info/:id', body: AtlasExperimentInfoComponent, data: { section: 'atlas' } }),
    launchpadRoute({ path: 'launchpad/atlas/experiment-feature-progress/:atlasId/:experimentId/:featureId', body: AtlasExperimentFeatureProgressComponent, data: { section: 'atlas' } }),


    //
    //
    //
    billingRoute({ path: 'billing', body: BillingComponent, data: { sidenavIsDisabled: true }, }),
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
        path: 'testview',
        loadChildren: './components/testview/testview.module#TestViewModule'
    },
    //
    //
    //
    {
        path: '',
        redirectTo: '',
        pathMatch: 'full'
    }
];

@NgModule({
    imports: [
        RouterModule.forRoot(routes)
    ],
    exports: [RouterModule],
    providers: []
})

export class AppRoutingModule {}