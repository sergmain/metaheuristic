import { DefaultResponse } from '@app/models/DefaultResponse';
import { DefaultListOfItems } from '@app/models/DefaultListOfItems';

export namespace resources {
    export namespace get {
        export interface Response extends DefaultResponse {
            items: Resources;
        }
    }
}

export interface Resources extends DefaultListOfItems {
    content: Resource[];
}

export interface Resource {
    id: number;
    version: number;
    code: string;
    poolCode: string;
    dataType: number;
    uploadTs: string;
    checksum ? : null;
    valid: boolean;
    manual: boolean;
    filename: string;
    storageUrl: string;
    dataTypeAsStr: string;
}