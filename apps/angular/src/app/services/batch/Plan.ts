export interface Plan {
    code: string;
    createdOn: number;
    id: string | number;
    locked: boolean;
    params: string;
    valid: boolean;
    version: number;
}