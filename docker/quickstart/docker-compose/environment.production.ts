import { IEnvironment } from './IEnvironment';

export const environment: IEnvironment = {
    production: true,
    baseUrl: 'http://172.28.1.3:8888/rest/v1/',
    hashLocationStrategy: true,
    userLifeTime: 30 * 60 * 1000, // 30 minutes
    isSslRequired: false,
    batchInterval: 15 * 1000, // in milliseconds
    language: 'EN', // other supported languages: 'EN'
    brandingTitle: 'Metaheuristic',
    brandingMsg: 'Metaheuristic' ,
    brandingMsgIndex: 'Metaheuristic',
};