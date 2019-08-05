export interface Task {
    id: number;
    version: number;
    params: string;
    stationId ? : number | null;
    assignedOn ? : number | null;
    completedOn ? : number | null;
    isCompleted: boolean;
    metrics ? : string | null;
    order: number;
    workbookId: number;
    execState: number;
    processType: number;
    resultReceived: boolean;
    resultResourceScheduledOn: number;
    completed: boolean;
}