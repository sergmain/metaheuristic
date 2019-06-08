import {
    environment
} from 'environments/environment';
import jsonToUrlParams from '@app/helpers/jsonToUrlParams'

const base = environment.baseUrl + '/ng/launchpad'

let urls = {
    stations: {
        get: data => `${base}/stations?${jsonToUrlParams(data)}`,
    },
    station: {
        get: `${base}/station/`,
        form: `${base}/station-form-commit/`,
        delete: `${base}/station-delete-commit/`,
    },
};

export {
    urls
}