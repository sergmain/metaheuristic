import { DefaultResponse } from '@app/models/DefaultResponse';
import { DefaultListOfItems } from '@app/models/DefaultListOfItems';
import { Pageable } from '@app/models/Pageable';
import { Sort } from '@app/models/Sort';



export namespace stations {
    export namespace get {
        export interface Response extends DefaultResponse {
            items: Stations;
        }
    }
}

export namespace station {
    export namespace get {
        export interface Response extends DefaultResponse {
            station: Station;
        }
    }
}

export interface Stations extends DefaultListOfItems {
    content: ListItemStation[];
}

export interface ListItemStation {
    active: boolean;
    blacklisted: boolean;
    host: string;
    ip: string;
    lastSeen: number;
    station: Station;
}

export interface Station {
    description: string;
    id: number;
    ip: string;
    status: string;
    updatedOn: number;
    version: number;
}