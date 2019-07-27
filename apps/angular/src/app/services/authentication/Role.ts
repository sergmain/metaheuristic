export enum Role {
    Admin = 'ROLE_ADMIN',
        Manager = 'ROLE_MANAGER',
        Operator = 'ROLE_OPERATOR',
        Billing = 'ROLE_BILLING',
        Data = 'ROLE_DATA',
        ServerRestAccess = 'ROLE_SERVER_REST_ACCESS'
}



// -- ROLE_ADMIN - полный доступ ко всем функциям на сайте
// -- ROLE_MANAGER
//    - доступ на чтение для функций Plan, Workbook, Snippet
//    - полный доступ к Batch
//    - нет доступа ко всем остальным фунцциям
// -- ROLE_OPERATOR - полный досуп ко всем функциям Batch
// -- ROLE_BILLING - полный досуп ко всем функциям Billing
// -- ROLE_DATA - полный доступ к функциям Plan, Workbook, Experiment, Atlas, Resource, Snippet