export default function jsonToUrlParams(data: any) {
    return Object.keys(data).map((k: string) => {
        if (data[k] === null || data[k] === undefined) {
            return false;
        }
        const value: string = data[k].toString();
        if (value !== '') {
            return encodeURIComponent(k) + '=' + encodeURIComponent(data[k]);
        }
    }).filter(Boolean).join('&');
}

export { jsonToUrlParams };