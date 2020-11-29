import { IEnvironment } from './IEnvironment';

export const environment: IEnvironment = {
    production: true,
    baseUrl: 'http://localhost:8888/rest/v1/',
    hashLocationStrategy: true,
    userLifeTime: 30 * 60 * 1000, // 30 minutes
    isSslRequired: false,
    batchInterval: 15 * 1000, // in milliseconds
    language: 'RU', // other supported languages: 'EN'
    brandingTitle: 'СТП Байкал',
    brandingMsg: '<p>Контактная информация:<br />' +
        'с вопросами и предложениями обращаться в РИЦ 411, г. Иркутск<br />' +
        '&#9742;  8(3952) 222-088</p>' +
        '<p>Корюков Алексей<br />' +
        '&#9993;  <a href="mailto:accounts@cons411.ru">accounts@cons411.ru</a></p>' +
        '<p>Бузовская Светлана<br />' +
        '&#9993;  <a href="mailto:sales@cons411.ru">sales@cons411.ru</a></p>' ,
    brandingMsgIndex: '<p>Контактная информация:<br />' +
        'с вопросами и предложениями обращаться в РИЦ 411, г. Иркутск<br />' +
        '&#9742;  8(3952) 222-088</p>',
};