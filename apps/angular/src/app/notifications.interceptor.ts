import {
    Injectable
} from '@angular/core';
import {
    HttpRequest,
    HttpHandler,
    HttpEvent,
    HttpInterceptor,
    HttpResponse,
    HttpErrorResponse
} from '@angular/common/http';
import {
    Observable,
    throwError
} from 'rxjs';
import {
    tap,
    catchError
} from 'rxjs/operators';
import {
    NotificationsService,
    NotificationType
} from 'angular2-notifications';

@Injectable()

export class NotificationsInterceptor implements HttpInterceptor {

    options = {
        timeOut: 10000,
        showProgressBar: true,
        pauseOnHover: true,
        clickToClose: false,
    };

    constructor(
        private notificationsService: NotificationsService
    ) {}

    intercept(req: HttpRequest < any > , next: HttpHandler): Observable < HttpEvent < any >> {

        return next.handle(req).pipe(
            tap((event: HttpEvent < any > ) => {
                if (event instanceof HttpResponse) {
                    event = event.clone({
                        body: this.modifyBody(event.body)
                    });
                }
                return event;
            }),
            catchError((error: HttpErrorResponse) => {
                if (error.status >= 400) {
                    if (error.error) {
                        this.notificationsService.error(error.error.status, error.error.message, {
                            // timeOut: 10000,
                            // showProgressBar: true,
                            pauseOnHover: true,
                            clickToClose: true,
                        });
                    }
                }
                return throwError(error);
            })
        );

    }

    private modifyBody(body: any): any {
        const status: string = body.status;
        const errors: string[] = body.errorMessages || [];
        const infos: string[] = body.infoMessages || [];
        errors.forEach((err: string) => {
            this.notificationsService.error(status, err, {
                // timeOut: 10000,
                // showProgressBar: true,
                pauseOnHover: true,
                clickToClose: true,
            });
        });
        infos.forEach((info: string) => {
            if (status.toLowerCase() === 'ok') {
                this.notificationsService.success(status, info, {
                    timeOut: 10000,
                    showProgressBar: true,
                    pauseOnHover: true,
                    clickToClose: false,
                });
            } else {
                this.notificationsService.info(status, info, {
                    // timeOut: 10000,
                    // showProgressBar: true,
                    pauseOnHover: true,
                    clickToClose: true,
                });
            }
        });
        if (errors.length === 0 && infos.length === 0 && status) {
            this.notificationsService.success(status, null, {
                timeOut: 10000,
                showProgressBar: true,
                pauseOnHover: true,
                clickToClose: false,
            });
        }
    }
}