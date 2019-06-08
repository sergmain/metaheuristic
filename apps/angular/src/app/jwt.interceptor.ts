import {
    Injectable
} from '@angular/core';
import {
    HttpRequest,
    HttpHandler,
    HttpEvent,
    HttpInterceptor
} from '@angular/common/http';
import {
    Observable
} from 'rxjs';

@Injectable()

export class JwtInterceptor implements HttpInterceptor {
    intercept(request: HttpRequest < any > , next: HttpHandler): Observable < HttpEvent < any >> {
        let user = JSON.parse(localStorage.getItem('user'));
        if (user) {
            request = request.clone({
                setHeaders: {
                    Authorization: user.token
                }
            });
        }

        return next.handle(request);
    }
}