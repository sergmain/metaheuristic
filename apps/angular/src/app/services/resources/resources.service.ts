import {
    Injectable
} from '@angular/core';
import {
    HttpClient
} from '@angular/common/http';
import {
    Observable,
} from 'rxjs';
import {
    ResourcesResponse
} from '@app/models';
import {
    urls
} from './urls';

@Injectable({
    providedIn: 'root'
})

export class ResourcesService {
    private items: (ResourcesResponse.Resource)[];


    constructor(
        private http: HttpClient
    ) {}

    resources = {
        get: (page) => this.http.get(urls.resources.get({
            page
        }))
    };

    resource = {
        // get: () => this.http.get(urls.resources.get()),

        upload: (formData) => this.http.post(urls.resource.upload(), formData),

        external: (resource) => this.http.post(urls.resource.external({
            resource
        }), resource),

        delete: (id) => this.http.post(urls.resource.delete({
            id
        }), {
            id
        }),
    };
}