export namespace ExperimentFeatureProgressResponse {
  export interface Response {
    errorMessages?: null;
    infoMessages?: null;
    metricsResult: MetricsResult;
    hyperParamResult: HyperParamResult;
    tasksResult: TasksResult;
    experiment: Experiment;
    experimentFeature: ExperimentFeature;
    consoleResult: ConsoleResult;
  }
  export interface MetricsResult {
    metricNames?: (string)[] | null;
    metrics?: (MetricsEntity)[] | null;
  }
  export interface MetricsEntity {
    values?: (number)[] | null;
    params?: null;
  }
  export interface HyperParamResult {
    elements?: (ElementsEntity)[] | null;
  }
  export interface ElementsEntity {
    key: string;
    list?: (ListEntity)[] | null;
    selectable: boolean;
  }
  export interface ListEntity {
    param: string;
    selected: boolean;
  }
  export interface TasksResult {
    errorMessages?: null;
    infoMessages?: null;
    items: Items;
  }
  export interface Items {
    content?: (ContentEntity)[] | null;
    pageable: Pageable;
    number: number;
    size: number;
    sort: Sort;
    first: boolean;
    numberOfElements: number;
    last: boolean;
    empty: boolean;
  }
  export interface ContentEntity {
    task: Task;
    type: number;
  }
  export interface Task {
    id: number;
    version: number;
    params: string;
    stationId?: number | null;
    assignedOn?: number | null;
    completedOn?: number | null;
    isCompleted: boolean;
    metrics?: string | null;
    order: number;
    workbookId: number;
    execState: number;
    processType: number;
    resultReceived: boolean;
    resultResourceScheduledOn: number;
    completed: boolean;
  }
  export interface Pageable {
    sort: Sort;
    offset: number;
    pageSize: number;
    pageNumber: number;
    unpaged: boolean;
    paged: boolean;
  }
  export interface Sort {
    sorted: boolean;
    unsorted: boolean;
    empty: boolean;
  }
  export interface Experiment {
    id: number;
    version: number;
    workbookId: number;
    name: string;
    description: string;
    code: string;
    seed: number;
    createdOn: number;
    numberOfTask: number;
    hyperParams?: (HyperParamsEntity)[] | null;
    allTaskProduced: boolean;
    featureProduced: boolean;
    hyperParamsAsMap: any | null;
  }
  export interface HyperParamsEntity {
    id: number;
    version: number;
    key: string;
    values: string;
    variants: number;
  }

  export interface ExperimentFeature {
    id: number;
    version: number;
    resourceCodes: string;
    checksumIdCodes: string;
    execStatus: number;
    experimentId: number;
    maxValue: number;
  }
  export interface ConsoleResult {
    items?: (null)[] | null;
  }
}