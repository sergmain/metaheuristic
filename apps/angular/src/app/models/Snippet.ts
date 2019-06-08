// export class Snippet {
//     name: string;
//     version: string;
//     type: string;
//     environment: string;
//     params: string;
//     isSigned: boolean;
//     constructor(p: any) {
//         this.name = p.name
//         this.version = p.version
//         this.type = p.type
//         this.environment = p.environment
//         this.params = p.params
//         this.isSigned = p.isSigned
//     }
// }


export class Snippet {
    id: number;
    version: number;
    name: string;
    type: string;
    snippetVersion: string;
    filename: string;
    params ? : null;
    checksum: string;
    env: string;
    isSigned: boolean;
    reportMetrics: boolean;
    length: number;
    fileProvided: boolean;
    signed: boolean;
    snippetCode: string;
    constructor(p: any) {
        this.id = p.id
        this.version = p.version
        this.name = p.name
        this.type = p.type
        this.snippetVersion = p.snippetVersion
        this.filename = p.filename
        this.params = p.params
        this.checksum = p.checksum
        this.env = p.env
        this.isSigned = p.isSigned
        this.reportMetrics = p.reportMetrics
        this.length = p.length
        this.fileProvided = p.fileProvided
        this.signed = p.signed
        this.snippetCode = p.snippetCode
    }
}