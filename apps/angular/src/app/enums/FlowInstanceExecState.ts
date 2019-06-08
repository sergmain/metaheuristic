export enum FlowInstanceExecState {
    NONE, // just created flow instance
    PRODUCING, // producing was just started
    PRODUCED, // producing was finished
    STARTED, // started
    STOPPED, // stopped
    FINISHED, // finished
    DOESNT_EXIST, // doesn't exist. this state is needed at station side to reconcile list of experiments
    UNKNOWN, // unknown state
    ERROR // some error in configuration
}