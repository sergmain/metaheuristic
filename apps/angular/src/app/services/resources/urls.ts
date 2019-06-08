import {
    environment
} from 'environments/environment';
import jsonToUrlParams from '@app/helpers/jsonToUrlParams';


const base = environment.baseUrl + '/rest/v1/launchpad/resource';

const urls = {
    resources: {
        get: (data) => `${base}/resources?${jsonToUrlParams(data)}`,
    },
    resource: {
        get: () => `${base}/resource/`,
        upload: () => `${base}/resource-upload-from-file/`,
        external: (data) => `${base}/resource-in-external-storage?${jsonToUrlParams(data)}`,
        delete: (data) => `${base}/resource-delete-commit?${jsonToUrlParams(data)}`,
    }
};



export {
    urls
};