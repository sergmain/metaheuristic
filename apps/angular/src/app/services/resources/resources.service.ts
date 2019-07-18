import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { urls } from './urls';

export * from './response';

@Injectable({ providedIn: 'root' })

export class ResourcesService {

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