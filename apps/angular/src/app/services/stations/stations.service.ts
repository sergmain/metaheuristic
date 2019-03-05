

import { Injectable } from '@angular/core';

export interface Station {
    id: string,
    ipAddress: string,
    description: string,
    environment: string,
    activeTime: string,
}

function rand(min, max) {
    return Math.floor(Math.random() * (max - min)) + min;
}

function initItem() {
    return {
        id: 'id' + rand(1, 9999),
        ipAddress: '' + rand(0, 256) + '.' + rand(0, 256) + '.' + rand(0, 256) + '.' + rand(0, 256),
        description: 'description-' + rand(1, 9999),
        environment: 'environment-' + rand(1, 9999),
        activeTime: 'activeTime-' + rand(1, 9999),

    }
}

function initItems(): Station[] {
    return Array.from(Array(99)).map(el => initItem())
}

@Injectable({
    providedIn: 'root'
})

export class StationsService {
    private data: Station[] = initItems();
    constructor() { }
    getStations(): Station[] {
        return this.data
    }
}