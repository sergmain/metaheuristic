import { jsonToUrlParams as toURL } from '@app/helpers/jsonToUrlParams';
import { environment } from 'environments/environment';

const base: string = environment.baseUrl + 'launchpad';

export const urls: any = {
    stations: {
        get: (data: any): string => `${base}/stations?${toURL(data)}`,
    },
    station: {
        get: (id: string | number): string => `${base}/station/${id}`,
        form: (station: any): string => `${base}/station-form-commit/`,
        delete: (id: string | number): string => `${base}/station-delete-commit/?id=${id}`
    }
};