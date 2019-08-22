export interface MetricsResult {
    metricNames ? : string[] | null;
    metrics ? : MetricsEntity[] | null;
}
export interface MetricsEntity {
    values ? : number[] | null;
    params ? : null;
}