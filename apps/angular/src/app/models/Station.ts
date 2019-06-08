export class Station {
    id: string;
    ip: string;
    description: string;
    env: string;
    activeTime: string;
    constructor(data) {
        this.id = data.id
        this.ip = data.ip
        this.description = data.description
        this.env = data.env
        this.activeTime = data.activeTime
    }
}