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
import { CtColComponent } from './custom-tags/ct-col/ct-col.component';
import { CtColsComponent } from './custom-tags/ct-cols/ct-cols.component';
import { CtSectionCaptionComponent } from './custom-tags/ct-section-caption/ct-section-caption.component';
import { JwtInterceptor } from './jwt.interceptor';
import { MaterialAppModule } from './ngmaterial.module';
import { NotificationsInterceptor } from './notifications.interceptor';
import { AboutComponent } from './views/about/about.component';
import { AppViewComponent } from './views/app-view/app-view.component';
import { AccountsComponent, AddAccountComponent, AddExperimentComponent, AddPlanComponent, AddWorkbookComponent, AddResourceComponent, AddSnippetComponent, EditAccountComponent, EditExperimentComponent, EditPlanComponent, EditWorkbookComponent, EditPassAccountComponent, EditStationComponent, ExperimentsComponent, PlansComponent, InfoExperimentComponent, WorkbooksComponent, LaunchpadComponent, NavLaunchpadComponent, ProgressExperimentComponent, ResourcesComponent, SnippetsComponent, StationsComponent } from './views/launchpad';
import { LoginComponent } from './views/login/login.component';
import { AppDialogConfirmationComponent } from './views/app-dialog-confirmation/app-dialog-confirmation.component';
import { NavPilotComponent, PilotComponent, ProcessResourcesComponent } from './views/pilot';
import { CtTableComponent } from './custom-tags/ct-table/ct-table.component';
import { CtWrapBlockComponent } from './custom-tags/ct-wrap-block/ct-wrap-block.component';

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
        AppDialogConfirmationComponent,
        // custom-tags
        CtColsComponent,
        CtSectionCaptionComponent,
        CtColComponent,
        CtTableComponent,
        CtWrapBlockComponent
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