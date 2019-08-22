import { DefaultListOfItems } from '@app/models';
import { Task } from './Task';


export interface Tasks extends DefaultListOfItems {
    content: Task[];
}