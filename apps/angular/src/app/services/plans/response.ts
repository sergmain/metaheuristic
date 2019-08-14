import { DefaultResponse } from "@src/app/models";
import { Plan } from './Plan';


export namespace response {
    export namespace plans {
        export interface Get {}
    }
    export namespace plan {
        export interface Get {}

        export interface Update {}

        export interface Validate {}

        export interface Delete {}

        export interface Archive {}
        
        export interface Add extends DefaultResponse {
            plan: Plan;
        }
    }

}