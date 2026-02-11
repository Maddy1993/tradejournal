create table schema_migrations (
   version           varchar2(50) primary key,
   description       varchar2(200) not null,
   checksum          varchar2(100) not null,
   execution_time_ms number not null,
   installed_on      timestamp default current_timestamp not null
);