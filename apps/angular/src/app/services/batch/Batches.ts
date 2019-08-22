import { Pageable } from '@app/models/Pageable';
import { Sort } from '@app/models/Sort';
import { Batch } from './Bacth';
export interface Batches {
    content ? : Batch[] | null;
    pageable: Pageable;
    totalPages: number;
    last: boolean;
    totalElements: number;
    size: number;
    sort: Sort;
    first: boolean;
    numberOfElements: number;
    number: number;
    empty: boolean;
}