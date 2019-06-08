export class Plan {
    clean:boolean;
    code:string;
    createdOn:string;
    id: string;
    locked:boolean;
    valid: string;
    params: string;
    version: string;
    constructor(p: any) {
        this.id = p.id
        this.code = p.code
        this.createdOn = p.createdOn
        this.valid = p.valid 
        this.locked = p.locked 
        this.params = p.params
        this.version = p.version
        this.clean = p.clean
    }
}
 