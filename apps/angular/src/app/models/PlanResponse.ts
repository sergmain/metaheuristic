export namespace PlanResponse {
    export interface Response {
        errorMessages: (string)[];
        infoMessages ?: null;
        plan: Plan;
        status: string;
    }
    export interface Plan {
        id: number;
        version: number;
        code: string;
        createdOn: number;
        params: string;
        locked: boolean;
        valid: boolean;
        clean: boolean;
    }
}


