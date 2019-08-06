import { CommonModule } from '@angular/common';
import { HttpClient, HttpClientModule, HTTP_INTERCEPTORS } from '@angular/common/http';
import { NgModule } from '@angular/core';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { BrowserModule } from '@angular/platform-browser';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import * as components from '@app/components';
import { AuthGuard } from '@app/guards/auth/auth.guard';
import { AccountsService } from '@app/services/accounts/accounts.service';
import { AuthenticationService } from '@app/services/authentication/authentication.service';
import { TranslateModule, TranslateLoader } from '@ngx-translate/core';
import { TranslateHttpLoader } from '@ngx-translate/http-loader';
import { PlotlyModule } from 'angular-plotly.js';
import { SimpleNotificationsModule } from 'angular2-notifications';
import { NgxJsonViewerModule } from 'ngx-json-viewer';
import { AppComponent } from './app.component';
import { AppRoutingModule } from './app.routing.module';
import { CtAppModule } from './ct.module';
import { JwtInterceptor } from './jwt.interceptor';
import { MaterialAppModule } from './ngmaterial.module';
import { NotificationsInterceptor } from './notifications.interceptor';


export function HttpLoaderFactory(http: HttpClient) {
    return new TranslateHttpLoader(http);
}


@NgModule({
    declarations: [
        AppComponent,
        components.AppViewComponent,
        components.NavLaunchpadComponent,
        components.LaunchpadComponent,
        components.AccountsComponent,
        components.AddAccountComponent,
        components.AccountEditComponent,
        components.AccountEditPassComponent,
        components.ExperimentsComponent,
        components.ExperimentAddComponent,
        components.ExperimentEditComponent,
        components.ExperimentInfoComponent,
        components.PlansComponent,
        components.PlanAddComponent,
        components.EditPlanComponent,
        components.PlansArchiveComponent,
        components.WorkbooksComponent,
        components.AddWorkbookComponent,
        components.EditWorkbookComponent,
        components.ResourcesComponent,
        components.ResourceAddComponent,
        components.SnippetsComponent,
        components.SnippetAddComponent,
        components.StationsComponent,
        components.StationEditComponent,
        components.PilotComponent,
        components.NavPilotComponent,
        components.AboutComponent,
        components.LoginComponent,
        components.AtlasExperimentsComponent,
        components.AtlasExperimentInfoComponent,
        components.AtlasExperimentFeatureProgressComponent,
        components.BatchComponent,
        components.BatchStatusComponent,
        components.BatchAddComponent,
        components.AppDialogConfirmationComponent,
        components.AtlasExperimentExportImportComponent,
        components.AccountsAccessComponent,
        components.BillingComponent,
        components.ExperimentFeatureProgressComponent,
        components.ExperimentMetricsComponent,
        components.ExperimentTasksComponent,
        components.AtlasExperimentTasksComponent,
        components.AtlasExperimentMetricsComponent
    ],
    imports: [
        CommonModule,
        PlotlyModule,
        BrowserModule,
        AppRoutingModule,
        BrowserAnimationsModule,
        MaterialAppModule,
        CtAppModule,
        FormsModule,
        ReactiveFormsModule,
        NgxJsonViewerModule,
        HttpClientModule,
        TranslateModule.forRoot({
            loader: {
                provide: TranslateLoader,
                useFactory: HttpLoaderFactory,
                deps: [HttpClient]
            }
        }),
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