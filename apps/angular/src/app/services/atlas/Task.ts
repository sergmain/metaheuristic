export interface Task {
    assignedOn: number;
    completed: boolean;
    completedOn: number;
    exec: string;
    execState: number;
    metrics: any;
    taskId: number;
    taskParams: string;
    typeAsString: string;
}