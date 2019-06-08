export default function jsonToUrlParams(data) {
    return Object.keys(data).map(function(k) {
        if (data[k] === null || data[k] === undefined) {
            return false
        }
        let value = data[k].toString()
        if (value != '') {
            return encodeURIComponent(k) + '=' + encodeURIComponent(data[k])
        }
    }).filter(Boolean).join('&')
}