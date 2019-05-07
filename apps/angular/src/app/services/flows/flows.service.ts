import { Injectable } from '@angular/core';

export class Instance {
    id: string;
    planCode: string;
    inputPoolCodes: string;
    createdOn: string;
    isPlanValid: string;
    isWorkbookValid: string;
    execState: string;
    completedOn: string;
    constructor(p: any) {
        this.id = 'id' + p.id || 'id' + rand(111111, 999999);
        this.planCode = p.planCode || 'planCode-' + rand(1, 9999);
        this.inputPoolCodes = p.inputPoolCodes || 'inputPoolCodes-' + rand(1, 9999);
        this.createdOn = p.createdOn || rand(1, 31) + '.' + rand(1, 12) + '.2018';
        this.isPlanValid = p.isPlanValid || ['Yes', 'No'][rand(0, 2)];
        this.isWorkbookValid = p.isWorkbookValid || ['Yes', 'No'][rand(0, 2)];
        this.execState = p.execState || 'execState-' + rand(1, 9999);
        this.completedOn = p.completedOn || rand(1, 31) + '.' + rand(1, 12) + '.2018';
    }
}

export class Plan {
    id: string;
    codeOfPlan: string;
    createdOn: string;
    isValid: string;
    isLocked: string;
    parameters: string;
    instances: Instance[];
    constructor(p: any) {
        this.id = 'id' + p.id || 'id' + rand(111111, 999999);
        this.codeOfPlan = p.codeOfPlan || 'codeOfPlan-' + rand(1, 9999);
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

function initPlans(): Plan[] {
    return Array.from(Array(99)).map((el, i) => new Plan({ id: i + 1 }))
}

@Injectable({
    providedIn: 'root'
})

export class PlansService {
    private data: Plan[] = initPlans();

    constructor() {}

    getPlans(): Plan[] {
        return this.data
    }

    getInstancesByPlanId(id: string): Instance[] {
        return [].concat(this.data.find(el => el.id === id).instances)
    }

    getPlan(id: string): Plan {
        return Object.assign({}, this.data.find(el => el.id === id))
    }

    updatePlan(id, newPlan) {}

    deletePlan(id) {}

    getInstance(planId: string, instanceId: string) {}
}