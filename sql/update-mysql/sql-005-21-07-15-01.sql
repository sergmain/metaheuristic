ALTER TABLE mh_variable
    MODIFY name VARCHAR(250) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin not null;

ALTER TABLE mh_variable_global
    MODIFY name VARCHAR(250) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin not null;

ALTER TABLE mh_source_code
    MODIFY UID VARCHAR(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin not null;

ALTER TABLE mh_function
    MODIFY FUNCTION_CODE VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin not null;