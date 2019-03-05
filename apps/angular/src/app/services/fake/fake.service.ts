import { Injectable } from '@angular/core';
import { Task, Feature, Hyper, Metadata, Experiment } from '@app/services/experiments/experiments.service'

@Injectable({
    providedIn: 'root'
})
export class FakeService {

    constructor() {}
}


export function rand(min, max) {
    return Math.floor(Math.random() * (max - min)) + min;
}


export function fakeExperiments() {

    function initItem(i) {
        return {
            id: 'id' + i,
            name: 'name-' + rand(1, 9999),
            description: 'description' + ' ' + rand(1111, 9999) + ' ' + rand(1111, 9999) + ' ' + rand(1111, 9999),
            experimentCode: 'experimentCode' + ' ' + rand(1111, 9999),
            seed: '1',
            createdOn: rand(1, 32) + '.' + rand(1, 13) + '.2018',
            metadatas: new Array < Metadata > (),
            hyper: [
                new Hyper({ key: 'aaa', values: ['10', '20'] }),
                new Hyper({ key: 'bbb', values: ['bbb_1', 'bbb_2'] }),
                new Hyper({ key: 'ccc', values: ['ccc_1', 'ccc_2'] }),
                new Hyper({ key: 'ddd', values: ['50','83'] }),
                new Hyper({ key: 'eee', values: ['50'] }),

            ],
            features: [
                new Feature({
                    id: 1,
                    setOfFeatures: ['simple-dataset-IRIS_input_output_v2.txt'],
                    execStatus: "Unknown",
                    maxValue: '159.0',
                })
            ],
            tasks: [
                new Task({
                    id: rand(1, 99),
                    type: ['PREDICT', 'FIT'][rand(0, 2)],
                    isCompleted: ['true', 'false'][rand(0, 2)],
                    completedOn: rand(1, 32) + '.' + rand(1, 13) + '.2018',
                    assignedOn: rand(1, 32) + '.' + rand(1, 13) + '.2018',
                }),
                new Task({
                    id: rand(1, 99),
                    type: ['PREDICT', 'FIT'][rand(0, 2)],
                    isCompleted: ['true', 'false'][rand(0, 2)],
                    completedOn: rand(1, 32) + '.' + rand(1, 13) + '.2018',
                    assignedOn: rand(1, 32) + '.' + rand(1, 13) + '.2018',
                }),
                new Task({
                    id: rand(1, 99),
                    type: ['PREDICT', 'FIT'][rand(0, 2)],
                    isCompleted: ['true', 'false'][rand(0, 2)],
                    completedOn: rand(1, 32) + '.' + rand(1, 13) + '.2018',
                    assignedOn: rand(1, 32) + '.' + rand(1, 13) + '.2018',
                }),
                new Task({
                    id: rand(1, 99),
                    type: ['PREDICT', 'FIT'][rand(0, 2)],
                    isCompleted: ['true', 'false'][rand(0, 2)],
                    completedOn: rand(1, 32) + '.' + rand(1, 13) + '.2018',
                    assignedOn: rand(1, 32) + '.' + rand(1, 13) + '.2018',
                }),
                new Task({
                    id: rand(1, 99),
                    type: ['PREDICT', 'FIT'][rand(0, 2)],
                    isCompleted: ['true', 'false'][rand(0, 2)],
                    completedOn: rand(1, 32) + '.' + rand(1, 13) + '.2018',
                    assignedOn: rand(1, 32) + '.' + rand(1, 13) + '.2018',
                }),
                new Task({
                    id: rand(1, 99),
                    type: ['PREDICT', 'FIT'][rand(0, 2)],
                    isCompleted: ['true', 'false'][rand(0, 2)],
                    completedOn: rand(1, 32) + '.' + rand(1, 13) + '.2018',
                    assignedOn: rand(1, 32) + '.' + rand(1, 13) + '.2018',
                }),
                new Task({
                    id: rand(1, 99),
                    type: ['PREDICT', 'FIT'][rand(0, 2)],
                    isCompleted: ['true', 'false'][rand(0, 2)],
                    completedOn: rand(1, 32) + '.' + rand(1, 13) + '.2018',
                    assignedOn: rand(1, 32) + '.' + rand(1, 13) + '.2018',
                }),
                new Task({
                    id: rand(1, 99),
                    type: ['PREDICT', 'FIT'][rand(0, 2)],
                    isCompleted: ['true', 'false'][rand(0, 2)],
                    completedOn: rand(1, 32) + '.' + rand(1, 13) + '.2018',
                    assignedOn: rand(1, 32) + '.' + rand(1, 13) + '.2018',
                }),
                new Task({
                    id: rand(1, 99),
                    type: ['PREDICT', 'FIT'][rand(0, 2)],
                    isCompleted: ['true', 'false'][rand(0, 2)],
                    completedOn: rand(1, 32) + '.' + rand(1, 13) + '.2018',
                    assignedOn: rand(1, 32) + '.' + rand(1, 13) + '.2018',
                }),

            ]
        }
    }

    function initItems(): Experiment[] {
        return Array.from(Array(99)).map((el, i) => initItem(i))
    }

    return initItems()
}