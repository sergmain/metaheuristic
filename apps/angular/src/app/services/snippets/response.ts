import { DefaultResponse } from '@app/models/DefaultResponse';
import { DefaultListOfItems } from '@app/models/DefaultListOfItems';
import { Pageable } from '@app/models/Pageable';
import { Sort } from '@app/models/Sort';



export namespace snippets {
    export namespace get {
        export interface Response extends DefaultResponse {
            snippets: Snippet[];
        }
    }
}

// export namespace snippet {
//     export namespace get {
//         export interface Response extends DefaultResponse {
//             station: Station;
//         }
//     }
// }



export interface Snippet {
    id: number;
    version: number;
    code: string;
    type: string;
    params: string;
}