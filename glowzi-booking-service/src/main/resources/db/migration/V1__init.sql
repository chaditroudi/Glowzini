create table bookings (
                          id bigserial primary key,
                          customer_user_id bigint not null,
                          business_id bigint not null,
                          staff_id bigint not null,
                          service_id bigint not null,
                          start_at timestamp not null,
                          end_at timestamp not null,
                          status varchar(30) not null,
                          created_at timestamp not null default current_timestamp
);