import {
    environment
} from 'environments/environment';
import jsonToUrlParams from '@app/helpers/jsonToUrlParams';

const base = environment.baseUrl + 'launchpad/snippet';

const urls = {
    snippets: {
        get: (data) => `${base}/snippets?${jsonToUrlParams(data)}`,
    },
    snippet: {
        upload: () => `${base}/snippet-upload-from-file/`,
        delete: (id) => `${base}/snippet-delete/${id}`,
    },
};

export {
    urls
}