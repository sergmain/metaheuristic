import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { urls } from './urls';
import {} from './response';

export * from './response';

@Injectable({ providedIn: 'root' })

export class SnippetsService {
    constructor(
        private http: HttpClient
    ) {}

    snippets = {
        get: (page) => this.http.get(urls.snippets.get({ page }))

    };
    snippet = {
        upload: (formData) => this.http.post(urls.snippet.upload(), formData),
        delete: (id) => this.http.get(urls.snippet.delete(id))
    };
}