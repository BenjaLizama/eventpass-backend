CREATE TABLE capacity_reservations (
    order_item_id uuid NOT NULL,
    event_id uuid NOT NULL,
    quantity integer NOT NULL CHECK (quantity > 0),
    status varchar(16) NOT NULL,
    ticket_category_id uuid NOT NULL,
    CONSTRAINT capacity_reservations_status_check CHECK (status IN ('RESERVED', 'REJECTED', 'RELEASED'))
);

CREATE TABLE events (
    id uuid NOT NULL,
    created_at timestamptz NOT NULL,
    created_by varchar(255),
    deleted_at timestamptz,
    updated_at timestamptz,
    updated_by varchar(255),
    version bigint NOT NULL,
    banner_url varchar(255),
    category varchar(50) NOT NULL,
    description text,
    end_date timestamptz NOT NULL,
    organizer_id uuid NOT NULL,
    start_date timestamptz NOT NULL,
    status varchar(50) NOT NULL,
    title varchar(200) NOT NULL,
    venue_id uuid NOT NULL,
    CONSTRAINT events_category_check CHECK (category IN ('CONCERT', 'FESTIVAL', 'THEATER', 'SPORTS', 'CONFERENCE', 'COMEDY', 'OTHER')),
    CONSTRAINT events_status_check CHECK (status IN ('DRAFT', 'PUBLISHED', 'PAUSED', 'CANCELLED', 'COMPLETED'))
);

CREATE TABLE ticket_categories (
    id uuid NOT NULL,
    created_at timestamptz NOT NULL,
    created_by varchar(255),
    deleted_at timestamptz,
    updated_at timestamptz,
    updated_by varchar(255),
    version bigint NOT NULL,
    available_capacity integer NOT NULL,
    max_per_user integer NOT NULL,
    name varchar(100) NOT NULL,
    price numeric(12,2) NOT NULL,
    total_capacity integer NOT NULL,
    event_id uuid NOT NULL
);

CREATE TABLE venues (
    id uuid NOT NULL,
    created_at timestamptz NOT NULL,
    created_by varchar(255),
    deleted_at timestamptz,
    updated_at timestamptz,
    updated_by varchar(255),
    version bigint NOT NULL,
    address varchar(255) NOT NULL,
    capacity integer NOT NULL,
    city varchar(100) NOT NULL,
    country varchar(100) NOT NULL,
    image_url varchar(255),
    latitude numeric(11,8),
    longitude numeric(11,8),
    name varchar(150) NOT NULL,
    place_id varchar(255)
);

ALTER TABLE ONLY capacity_reservations
    ADD CONSTRAINT capacity_reservations_pkey PRIMARY KEY (order_item_id);

ALTER TABLE ONLY events
    ADD CONSTRAINT events_pkey PRIMARY KEY (id);

ALTER TABLE ONLY ticket_categories
    ADD CONSTRAINT ticket_categories_pkey PRIMARY KEY (id);

ALTER TABLE ONLY venues
    ADD CONSTRAINT venues_pkey PRIMARY KEY (id);

CREATE INDEX idx_event_category ON events (category);


--
-- Name: idx_event_organizer_id;

CREATE INDEX idx_event_organizer_id ON events (organizer_id);


--
-- Name: idx_event_status_start_date;

CREATE INDEX idx_event_status_start_date ON events (status, start_date);


--
-- Name: idx_ticket_category_event_id;

CREATE INDEX idx_ticket_category_event_id ON ticket_categories (event_id);


--
-- Name: idx_venue_city;

CREATE INDEX idx_venue_city ON venues (city);


--
-- Name: idx_venue_place_id;

CREATE INDEX idx_venue_place_id ON venues (place_id);


--
-- Name: ticket_categories fk1gvdrrce3gao9i4m6rb0xbw4x;

ALTER TABLE ONLY ticket_categories
    ADD CONSTRAINT fk1gvdrrce3gao9i4m6rb0xbw4x FOREIGN KEY (event_id) REFERENCES events(id);

ALTER TABLE ONLY events
    ADD CONSTRAINT fkqdxygdernwwt74hdvix9u5nr3 FOREIGN KEY (venue_id) REFERENCES venues(id);
