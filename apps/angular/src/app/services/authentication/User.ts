import { Authority, user } from './response';
import { Role } from './Role';
export class User {

    authorities: Authority[];
    publicName: string;
    username: string;

    constructor(data: user.get.Response) {
        if (!data) {
            data = {} as user.get.Response;
        }
        this.publicName = data.publicName || '';
        this.username = data.username || '';
        this.authorities = data.authorities || [];
    }

    getRoleSet(): Set < Role > {
        const set: Set < Role > = new Set();
        this.authorities.forEach((authority: Authority) => {
            set.add(authority.authority);
        });
        return set;
    }
}