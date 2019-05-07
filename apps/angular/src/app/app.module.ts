import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import { BrowserModule } from '@angular/platform-browser';
import { FormsModule } from '@angular/forms';
import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MaterialAppModule } from './ngmaterial.module';
import { AppRoutingModule } from './app-routing.module';
import { AppComponent } from './app.component';
import { PlotlyModule } from 'angular-plotly.js';
import { NgxJsonViewerModule } from 'ngx-json-viewer';
// 
import { AppViewComponent } from './views/app-view/app-view.component';
// 
import { AuthService } from '@app/services/auth/auth.service'
import { AccountsService } from '@app/services/accounts/accounts.service'
import {
    NavLaunchpadComponent,
    LaunchpadComponent,

    AccountsComponent,
    AddAccountComponent,
    EditAccountComponent,
    EditPassAccountComponent,

    ExperimentsComponent,
    AddExperimentComponent,
    EditExperimentComponent,
    InfoExperimentComponent,
    ProgressExperimentComponent,

    PlansComponent,
    AddPlanComponent,
    EditPlanComponent,

    InstancesComponent,
    AddInstanceComponent,
    EditInstanceComponent,

    ResourcesComponent,
    AddResourceComponent,

    SnippetsComponent,
    StationsComponent,
} from './views/launchpad';
// 
import { PilotComponent } from './views/pilot/pilot.component';
import { AboutComponent } from './views/about/about.component';
import { SigninComponent } from './views/signin/signin.component';
// 
import { CtColsComponent } from './custom-tags/ct-cols/ct-cols.component';
import { CtSectionCaptionComponent } from './custom-tags/ct-section-caption/ct-section-caption.component';
import { CtColComponent } from './custom-tags/ct-col/ct-col.component';

// 
@NgModule({
    declarations: [
        AppComponent,
        AppViewComponent,
        // launchpad
        NavLaunchpadComponent,
        LaunchpadComponent,

        AccountsComponent,
        AddAccountComponent,
        EditAccountComponent,
        EditPassAccountComponent,

        ExperimentsComponent,
        AddExperimentComponent,
        EditExperimentComponent,
        InfoExperimentComponent,
        ProgressExperimentComponent,

        PlansComponent,
        AddPlanComponent,
        EditPlanComponent,

        InstancesComponent,
        AddInstanceComponent,
        EditInstanceComponent,

        ResourcesComponent,
        AddResourceComponent,

        SnippetsComponent,
        StationsComponent,
        //
        PilotComponent,
        //
        AboutComponent,
        //
        SigninComponent,
        // custom-tags
        CtColsComponent,
        CtSectionCaptionComponent,
        CtColComponent,
    ],
    imports: [
        CommonModule,
        PlotlyModule,
        BrowserModule,
        AppRoutingModule,
        BrowserAnimationsModule,
        MaterialAppModule,
        FormsModule,
        NgxJsonViewerModule
    ],
    providers: [AuthService, AccountsService],
    bootstrap: [AppComponent]
})
export class AppModule {}