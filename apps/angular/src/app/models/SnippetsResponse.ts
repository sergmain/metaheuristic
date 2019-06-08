// tslint:disable-next-line: no-namespace
export namespace SnippetsResponse {
    export interface Response {
        errorMessages ?: null;
        infoMessages ?: null;
        snippets ?: (Snippet)[] | null;
    }
    export interface Snippet {
        id: number;
        version: number;
        code: string;
        type: string;
        params ?: string | null;
    }
}