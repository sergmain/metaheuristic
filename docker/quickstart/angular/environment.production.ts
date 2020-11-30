/*
 * Metaheuristic, Copyright (C) 2017-2020  Serge Maslyukov
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

// @ts-ignore
import { IEnvironment } from './IEnvironment';

export const environment: IEnvironment = {
    production: true,
    baseUrl: 'http://localhost:8083/rest/v1/',
    hashLocationStrategy: true,
    userLifeTime: 30 * 60 * 1000, // 30 minutes
    isSslRequired: false,
    batchInterval: 15 * 1000, // in milliseconds
    language: 'EN', // other supported languages: 'EN'
    brandingTitle: 'Metaheuristic',
    brandingMsg: 'Metaheuristic' ,
    brandingMsgIndex: 'Metaheuristic',
};