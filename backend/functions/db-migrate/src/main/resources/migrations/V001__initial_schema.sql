-- Initial Schema

create table users (
   id         varchar2(50) primary key,
   email      varchar2(100) not null unique,
   created_at timestamp default current_timestamp
);

create table accounts (
   id             varchar2(50) primary key,
   user_id        varchar2(50)
      references users ( id ),
   brokerage      varchar2(100),
   account_number varchar2(100),
   balance        number(15,2),
   updated_at     timestamp default current_timestamp
);

create table trades (
   id         varchar2(50) primary key,
   account_id varchar2(50)
      references accounts ( id ),
   symbol     varchar2(20) not null,
   quantity   number(10,4) not null,
   price      number(10,2) not null,
   side       varchar2(10) check ( side in ( 'BUY',
                                       'SELL' ) ),
   trade_date timestamp not null,
   created_at timestamp default current_timestamp
);