create table users (
                       id bigserial primary key,
                       phone varchar(20) not null unique,
                       password_hash varchar(255) not null,
                       role varchar(30) not null,
                       created_at timestamp not null default current_timestamp
);