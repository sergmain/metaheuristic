import { Role } from './Role';

export namespace user {
    export namespace get {
        export interface Response {
            authorities: Authority[];
            publicName: string;
            username: string;
        }
    }
}


export interface Authority {
    authority: Role;
}