import { Sort, Pageable } from '@app/models';

export interface Tasks {
    content ? : (ContentEntity)[] | null;
    pageable: Pageable;
    number: number;
    size: number;
    sort: Sort;
    first: boolean;
    numberOfElements: number;
    last: boolean;
    empty: boolean;
}
export interface ContentEntity {
    task: Task;
    type: number;
}
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