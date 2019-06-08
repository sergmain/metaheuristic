// tslint:disable-next-line: no-namespace
export namespace StationResponse {
    export interface Response {
        errorMessages ?: null;
        infoMessages ?: null;
        station: Station;
    }
    export interface Station {
        id: number;
        version: number;
        ip ?: null;
        description ?: null;
        env: string;
        activeTime ?: null;
    }
}