import { CommonModule } from '@angular/common';
import { HttpClientModule, HTTP_INTERCEPTORS } from '@angular/common/http';
import { NgModule } from '@angular/core';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { BrowserModule } from '@angular/platform-browser';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import { AuthGuard } from '@app/guards/auth/auth.guard';
import { AccountsService } from '@app/services/accounts/accounts.service';
import { AuthenticationService } from '@app/services/authentication/authentication.service';
import { PlotlyModule } from 'angular-plotly.js';
import { SimpleNotificationsModule } from 'angular2-notifications';
import { NgxJsonViewerModule } from 'ngx-json-viewer';
import { AppRoutingModule } from './app-routing.module';
import { AppComponent } from './app.component';
import { JwtInterceptor } from './jwt.interceptor';
import { MaterialAppModule } from './ngmaterial.module';
import { NotificationsInterceptor } from './notifications.interceptor';

import * as components from '@app/components';


@NgModule({
    declarations: [
        AppComponent,
        components.AppViewComponent,
        // launchpad
        components.NavLaunchpadComponent,
        components.LaunchpadComponent,

        components.AccountsComponent,
        components.AddAccountComponent,
        components.EditAccountComponent,
        components.EditPassAccountComponent,

        components.ExperimentsComponent,
        components.AddExperimentComponent,
        components.EditExperimentComponent,
        components.InfoExperimentComponent,
        components.ProgressExperimentComponent,

        components.PlansComponent,
        components.AddPlanComponent,
        components.EditPlanComponent,
        components.ArchivePlansComponent,

        components.WorkbooksComponent,
        components.AddWorkbookComponent,
        components.EditWorkbookComponent,

        components.ResourcesComponent,
        components.AddResourceComponent,

        components.SnippetsComponent,
        components.AddSnippetComponent,

        components.StationsComponent,
        components.EditStationComponent,
        //
        components.PilotComponent,
        components.NavPilotComponent,
        components.ProcessResourcesComponent,
        //
        components.AboutComponent,
        //
        components.LoginComponent,
        //
        components.AtlasComponent,
        // TODO rename AtlasInfoComponent
        components.AtlasInfoComponent,
        components.AtlasExperimentFeatureProgressComponent,
        //
        components.BatchComponent,
        components.BatchStatusComponent,
        components.BatchAddComponent,
        //
        components.AppDialogConfirmationComponent,
        components.FileUploaderComponent,

        // custom-tags
        components.CtColsComponent,
        components.CtSectionCaptionComponent,
        components.CtColComponent,
        components.CtTableComponent,
        components.CtWrapBlockComponent,
    ],
    imports: [
        CommonModule,
        PlotlyModule,
        BrowserModule,
        AppRoutingModule,
        BrowserAnimationsModule,
        MaterialAppModule,
        FormsModule,
        ReactiveFormsModule,
        NgxJsonViewerModule,
        HttpClientModule,
        SimpleNotificationsModule.forRoot()
    ],
    entryComponents: [
        components.AppDialogConfirmationComponent
    ],
    providers: [
        AuthGuard,
        AuthenticationService,
        AccountsService,
        {
            provide: HTTP_INTERCEPTORS,
            useClass: JwtInterceptor,
            multi: true
        },
        {
            provide: HTTP_INTERCEPTORS,
            useClass: NotificationsInterceptor,
            multi: true
        }
    ],
    bootstrap: [AppComponent]
})
export class AppModule {}