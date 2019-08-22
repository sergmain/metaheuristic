import { Authority } from './Authoritie';
export interface Account {
    accountNonExpired: boolean;
    accountNonLocked: boolean;
    authorities: Authority[];
    createdOn: number;
    credentialsNonExpired: boolean;
    enabled: boolean;
    id: number;
    login: string;
    mailAddress: null;
    password: null;
    password2: null;
    phone: number;
    phoneAsStr: null;
    publicName: string;
    roles: string;
    token: string;
    username: string;
    version: number;
}