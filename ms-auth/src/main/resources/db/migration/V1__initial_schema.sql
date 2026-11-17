CREATE TABLE credentials (
    id uuid NOT NULL,
    created_at timestamptz NOT NULL,
    created_by varchar(255),
    deleted_at timestamptz,
    updated_at timestamptz,
    updated_by varchar(255),
    version bigint NOT NULL,
    email varchar(255) NOT NULL,
    password varchar(255) NOT NULL,
    role varchar(255) NOT NULL,
    account_status varchar(255) NOT NULL,
    CONSTRAINT credentials_account_status_check CHECK (account_status IN ('ACTIVE', 'INACTIVE', 'BLOCKED')),
    CONSTRAINT credentials_role_check CHECK (role IN ('CUSTOMER', 'STAFF', 'ORGANIZER', 'SUPPORT', 'ADMIN', 'INTERNAL_SERVICE'))
);

ALTER TABLE ONLY credentials
    ADD CONSTRAINT credentials_pkey PRIMARY KEY (id);

ALTER TABLE ONLY credentials
    ADD CONSTRAINT uk6pka8top3ggqmjvppakv4ygl8 UNIQUE (email);

CREATE INDEX idx_credential_email ON credentials (email);
