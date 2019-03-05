

import { Injectable } from '@angular/core';

export interface Resource {
    isValid: boolean;
    uploadDate: string;
    typeOfResource: string;
    checksum: string;
    code: string;
    poolCode: string;
    isManual: boolean;
    filename: string;
    storageUrl: string;
}

function rand(min, max) {
    return Math.floor(Math.random() * (max - min)) + min;
}

function initItem() {
    return {
        isValid: [true, false][rand(0, 2)],
        uploadDate: rand(1, 32) + '.' + rand(1, 13) + '.2018',
        typeOfResource: ['DATA', ' -- '][rand(0, 2)],
        checksum: '--',
        code: '--',
        poolCode: '--',
        isManual: [true, false][rand(0, 2)],
        filename: '--',
        storageUrl: ['launchpad://', '--'][rand(0, 2)],
    }
}

function initItems(): Resource[] {
    return Array.from(Array(99)).map(el => initItem())
}

@Injectable({
    providedIn: 'root'
})

export class ResourcesService {
    private data: Resource[] = initItems();
    constructor() {}
    getResources(): Resource[] {
        return this.data
    }
}