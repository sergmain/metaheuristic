export interface HyperParamResult {
    elements ? : (ElementsEntity)[] | null;
}
export interface ElementsEntity {
    key: string;
    list ? : (ListEntity)[] | null;
    selectable: boolean;
}
export interface ListEntity {
    param: string;
    selected: boolean;
}