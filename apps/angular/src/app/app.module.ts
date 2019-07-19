import { CommonModule } from '@angular/common';
import { HttpClientModule, HTTP_INTERCEPTORS } from '@angular/common/http';
import { NgModule } from '@angular/core';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { BrowserModule } from '@angular/platform-browser';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import { AuthGuard } from '@app/guards/auth/auth.guard';
import { AccountsService } from '@app/services/accounts/accounts.service';
// import { AuthService } from '@app/services/auth/auth.service'
import { AuthenticationService } from '@app/services/authentication/authentication.service';
import { PlotlyModule } from 'angular-plotly.js';
import { SimpleNotificationsModule } from 'angular2-notifications';
import { NgxJsonViewerModule } from 'ngx-json-viewer';
import { AppRoutingModule } from './app-routing.module';
import { AppComponent } from './app.component';
// tslint:disable-next-line: max-line-length
import { AboutComponent, AccountsComponent, AddAccountComponent, AddExperimentComponent, AddPlanComponent, AddResourceComponent, AddSnippetComponent, AddWorkbookComponent, AppDialogConfirmationComponent, AppViewComponent, ArchivePlansComponent, AtlasComponent, BatchAddComponent, BatchComponent, BatchStatusComponent, CtColComponent, CtColsComponent, CtSectionCaptionComponent, CtTableComponent, CtWrapBlockComponent, EditAccountComponent, EditExperimentComponent, EditPassAccountComponent, EditPlanComponent, EditStationComponent, EditWorkbookComponent, ExperimentsComponent, FileUploaderComponent, InfoExperimentComponent, LaunchpadComponent, LoginComponent, NavLaunchpadComponent, NavPilotComponent, PilotComponent, PlansComponent, ProcessResourcesComponent, ProgressExperimentComponent, ResourcesComponent, SnippetsComponent, StationsComponent, WorkbooksComponent, AtlasInfoComponent,AtlasExperimentFeatureProgressComponent } from './components';
import { JwtInterceptor } from './jwt.interceptor';
import { MaterialAppModule } from './ngmaterial.module';
import { NotificationsInterceptor } from './notifications.interceptor';

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
        ArchivePlansComponent,

        WorkbooksComponent,
        AddWorkbookComponent,
        EditWorkbookComponent,

        ResourcesComponent,
        AddResourceComponent,

        SnippetsComponent,
        AddSnippetComponent,

        StationsComponent,
        EditStationComponent,
        //
        PilotComponent,
        NavPilotComponent,
        ProcessResourcesComponent,
        //
        AboutComponent,
        //
        LoginComponent,
        //
        AtlasComponent,
        // TODO rename AtlasInfoComponent
        AtlasInfoComponent,
        AtlasExperimentFeatureProgressComponent,
        //
        BatchComponent,
        BatchStatusComponent,
        BatchAddComponent,
        //
        AppDialogConfirmationComponent,
        FileUploaderComponent,

        // custom-tags
        CtColsComponent,
        CtSectionCaptionComponent,
        CtColComponent,
        CtTableComponent,
        CtWrapBlockComponent,
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
        SimpleNotificationsModule.forRoot(),
    ],
    entryComponents: [
        AppDialogConfirmationComponent
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