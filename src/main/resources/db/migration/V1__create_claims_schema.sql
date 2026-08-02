create table claims (
    id uuid primary key,
    claimant_id varchar(100) not null,
    claim_type varchar(30) not null,
    market varchar(2) not null,
    incident_at timestamp with time zone not null,
    description varchar(2000) not null,
    estimated_loss decimal(19, 2) not null,
    currency varchar(3) not null,
    status varchar(40) not null,
    assignee_id varchar(100),
    decision_reason varchar(2000),
    submitted_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    version bigint not null
);

create table claim_timeline (
    id uuid primary key,
    claim_id uuid not null references claims(id),
    event_type varchar(80) not null,
    resulting_status varchar(40) not null,
    actor_id varchar(100),
    detail clob,
    occurred_at timestamp with time zone not null
);

create table outbox_messages (
    id uuid primary key,
    claim_id uuid not null references claims(id),
    event_type varchar(80) not null,
    payload clob not null,
    occurred_at timestamp with time zone not null,
    processed_at timestamp with time zone
);

create index ix_claims_status_assignee on claims(status, assignee_id);
create index ix_claims_market_status on claims(market, status);
create index ix_timeline_claim_time on claim_timeline(claim_id, occurred_at);
create index ix_outbox_pending_time on outbox_messages(processed_at, occurred_at);
