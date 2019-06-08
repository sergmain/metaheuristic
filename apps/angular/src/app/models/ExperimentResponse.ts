import { Experiment } from '@app/models/Experiment'

export interface ExperimentResponse {
  errorMessages?: null;
  infoMessages?: null;
  experiment: Experiment;
}