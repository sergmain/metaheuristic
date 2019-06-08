export namespace ExperimentsResponse {
  export interface Response {
    errorMessages?: null;
    infoMessages?: null;
    items: Items;
  }
  export interface Items {
    content?: (ContentEntity)[] | null;
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
    id: number;
    version: number;
    workbookId: number;
    name: string;
    description: string;
    code: string;
    seed: number;
    createdOn: number;
    numberOfTask: number;
    hyperParams?: (HyperParamsEntity)[] | null;
    allTaskProduced: boolean;
    featureProduced: boolean;
    hyperParamsAsMap: any | null;
  }
  export interface HyperParamsEntity {
    id: number;
    version: number;
    key: string;
    values: string;
    variants: number;
  }
  export interface Pageable {
    sort: Sort;
    offset: number;
    pageSize: number;
    pageNumber: number;
    unpaged: boolean;
    paged: boolean;
  }
  export interface Sort {
    sorted: boolean;
    unsorted: boolean;
    empty: boolean;
  }
}