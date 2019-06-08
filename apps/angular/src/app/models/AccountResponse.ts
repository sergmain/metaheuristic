// tslint:disable-next-line: no-namespace
export namespace AccountResponse {
    export interface Response {
        errorMessages ?: null;
        infoMessages ?: null;
        account: Account;
    }
    export interface Account {
        id: number;
        version: number;
        username: string;
        password ?: null;
        password2 ?: null;
        accountNonExpired: boolean;
        accountNonLocked: boolean;
        credentialsNonExpired: boolean;
        enabled: boolean;
        publicName: string;
        mailAddress ?: null;
        phone: number;
        phoneAsStr ?: null;
        token: string;
        createdOn: number;
        roles: string;
        authorities ?: (AuthoritiesEntity)[] | null;
        login: string;
    }
    export interface AuthoritiesEntity {
        authority: string;
    }
}