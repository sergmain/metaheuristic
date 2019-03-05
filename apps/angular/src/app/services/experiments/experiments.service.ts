import { Injectable } from '@angular/core';
import { fakeExperiments } from '@app/services/fake/fake.service'

export class Metadata {
    key: string;
    value: string;

    constructor(key: string = '--none--', value: string = '--none--') {
        this.key = key
        this.value = value
    }

    setValue(key: string, value: string) {

    }
}

export class Task {
    id: string;
    type: string;
    isCompleted: string;
    completedOn: string;
    assignedOn: string;

    constructor(data) {
        this.id = data.id
        this.type = data.type
        this.isCompleted = data.isCompleted
        this.completedOn = data.completedOn
        this.assignedOn = data.assignedOn
    }
}

export class Hyper {
    key: string;
    values: string[];
    constructor(data) {
        this.key = data.key;
        this.values = data.values;
    }
}

export class Feature {
    id: string;
    setOfFeatures: string[];
    execStatus: string;
    maxValue: string;
    constructor(data) {
        this.id = data.id;
        this.setOfFeatures = data.setOfFeatures;
        this.execStatus = data.execStatus;
        this.maxValue = data.maxValue;
    }
}

export class Experiment {
    id: string;
    name: string;
    createdOn: string;
    description: string;
    experimentCode: string;
    seed: string;
    metadatas: Metadata[];
    hyper: Hyper[];
    features: Feature[];
    tasks: Task[];
    constructor(data) {
        this.id = data.id;
        this.name = data.name;
        this.createdOn = data.createdOn;
        this.description = data.description;
        this.experimentCode = data.experimentCode;
        this.seed = data.seed;
        this.metadatas = data.metadatas;
        this.hyper = data.hyper
        this.features = data.features
        this.tasks = data.tasks    
    }
}

@Injectable({
    providedIn: 'root'
})

export class ExperimentsService {
    private data: Experiment[] = fakeExperiments();

    constructor() {}

    getExperiments(): Experiment[] {
        return this.data
    }

    getExperiment(id): Experiment {
        return this.data.find(el => id === el.id)
    }

    setDefault(arr) {
        let data = {
            "epoch": "[10]",
            "RNN": "[LSTM, GRU, SimpleRNN]",
            "activation": "[hard_sigmoid, softplus, softmax, softsign, relu, tanh, sigmoid, linear, elu]",
            "optimizer": "[sgd, nadam, adagrad, adadelta, rmsprop, adam, adamax]",
            "batch_size": "[20, 40, 60]",
            "time_steps": "[5, 40, 60]",
            "metrics_functions": "['#in_top_draw_digit, accuracy', 'accuracy']"
        }

        for (let prop in data) {
            let metadata = arr.find(metadata => metadata.key == prop)
            if (metadata) {
                metadata.value = data[prop]
            } else {
                arr.push(new Metadata(prop, data[prop]))
            }
        }
        return arr
    }
}