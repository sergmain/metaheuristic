import { DefaultListOfItems, DefaultResponse } from '@app/models';
import { Task } from './Task';

interface ItemEntity extends DefaultListOfItems {
    content: TaskEntity[];
}

interface TaskEntity {
    task: Task;
    type: number;
}

export interface TasksResult extends DefaultResponse {
    items: ItemEntity;
}