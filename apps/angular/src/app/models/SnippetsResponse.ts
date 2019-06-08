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
        name: string;
        type: string;
        snippetVersion: string;
        filename: string;
        params ?: null;
        checksum: string;
        env: string;
        isSigned: boolean;
        reportMetrics: boolean;
        length: number;
        fileProvided: boolean;
        signed: boolean;
        snippetCode: string;
    }
}