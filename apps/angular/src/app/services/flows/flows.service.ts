import { Injectable } from '@angular/core';

export class Instance {
    id: string;
    flowCode: string;
    inputPoolCodes: string;
    createdOn: string;
    isFlowValid: string;
    isFlowInstanceValid: string;
    execState: string;
    completedOn: string;
    constructor(p: any) {
        this.id = 'id' + p.id || 'id' + rand(111111, 999999);
        this.flowCode = p.flowCode || 'flowCode-' + rand(1, 9999);
        this.inputPoolCodes = p.inputPoolCodes || 'inputPoolCodes-' + rand(1, 9999);
        this.createdOn = p.createdOn || rand(1, 31) + '.' + rand(1, 12) + '.2018';
        this.isFlowValid = p.isFlowValid || ['Yes', 'No'][rand(0, 2)];
        this.isFlowInstanceValid = p.isFlowInstanceValid || ['Yes', 'No'][rand(0, 2)];
        this.execState = p.execState || 'execState-' + rand(1, 9999);
        this.completedOn = p.completedOn || rand(1, 31) + '.' + rand(1, 12) + '.2018';
    }
}

export class Flow {
    id: string;
    codeOfFlow: string;
    createdOn: string;
    isValid: string;
    isLocked: string;
    parameters: string;
    instances: Instance[];
    constructor(p: any) {
        this.id = 'id' + p.id || 'id' + rand(111111, 999999);
        this.codeOfFlow = p.codeOfFlow || 'codeOfFlow-' + rand(1, 9999);
        this.createdOn = p.createdOn || rand(1, 31) + '.' + rand(1, 12) + '.2018';
        this.isValid = p.isValid || ['Yes', 'No'][rand(0, 2)];
        this.isLocked = p.isLocked || ['Yes', 'No'][rand(0, 2)];
        this.parameters = p.parameters || initParameters();
        this.instances = p.instances ||
            Array.from(Array(99)).map((el, i) => new Instance({ id: i + 1 }));
    }
}

function rand(min, max) {
    return Math.floor(Math.random() * (max - min)) + min;
}

function initParameters() {
    return `- parameters
    a ${rand(1111, 9999)}
    b ${rand(1111, 9999)}
    c ${rand(1111, 9999)}
`
}

function initFlows(): Flow[] {
    return Array.from(Array(99)).map((el, i) => new Flow({ id: i + 1 }))
}

@Injectable({
    providedIn: 'root'
})

export class FlowsService {
    private data: Flow[] = initFlows();

    constructor() {}

    getFlows(): Flow[] {
        return this.data
    }

    getInstancesByFlowId(id: string): Instance[] {
        return [].concat(this.data.find(el => el.id === id).instances)
    }

    getFlow(id: string): Flow {
        return Object.assign({}, this.data.find(el => el.id === id))
    }

    updateFlow(id, newFlow) {}

    deleteFlow(id) {}

    getInstance(flowId: string, instanceId: string) {}
}