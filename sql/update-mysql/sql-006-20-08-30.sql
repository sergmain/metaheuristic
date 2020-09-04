update mh_account
set PUBLIC_NAME = concat('user-', id)
where PUBLIC_NAME is null or length(PUBLIC_NAME)=0;

alter table mh_account modify PUBLIC_NAME varchar(100) not null;