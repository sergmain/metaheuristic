import { DefaultResponse } from '@src/app/models';
import { Account } from './Account';

export namespace account {
    export namespace get {
        export interface Response extends DefaultResponse {
            account: Account;
        }
    }
}