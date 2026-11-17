CREATE TABLE order_completed_outbox (
    order_id uuid NOT NULL,
    payload text NOT NULL,
    next_attempt_at timestamptz DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE TABLE order_items (
    id uuid NOT NULL,
    created_at timestamptz NOT NULL,
    created_by varchar(255),
    deleted_at timestamptz,
    updated_at timestamptz,
    updated_by varchar(255),
    version bigint NOT NULL,
    event_id uuid NOT NULL,
    order_id uuid NOT NULL,
    quantity integer NOT NULL,
    ticket_category_id uuid NOT NULL,
    unit_price numeric(19,2) NOT NULL
);

CREATE TABLE orders (
    id uuid NOT NULL,
    created_at timestamptz NOT NULL,
    created_by varchar(255),
    deleted_at timestamptz,
    updated_at timestamptz,
    updated_by varchar(255),
    version bigint NOT NULL,
    expires_at timestamptz NOT NULL,
    payment_status varchar(20) NOT NULL,
    status varchar(20) NOT NULL,
    total_amount numeric(19,2) NOT NULL,
    user_id uuid NOT NULL,
    CONSTRAINT orders_payment_status_check CHECK (payment_status IN ('PENDING', 'APPROVED', 'REJECTED', 'REFUNDED')),
    CONSTRAINT orders_status_check CHECK (status IN ('PENDING', 'AWAITING_PAYMENT', 'PENDING_ISSUANCE', 'PAID', 'REJECTED_NO_CAPACITY', 'CANCELLED', 'EXPIRED'))
);

ALTER TABLE ONLY order_completed_outbox
    ADD CONSTRAINT order_completed_outbox_pkey PRIMARY KEY (order_id);

ALTER TABLE ONLY order_items
    ADD CONSTRAINT order_items_pkey PRIMARY KEY (id);

ALTER TABLE ONLY orders
    ADD CONSTRAINT orders_pkey PRIMARY KEY (id);

CREATE INDEX idx_order_completed_outbox_next_attempt ON order_completed_outbox (next_attempt_at, order_id);


--
-- Name: idx_order_items_event_id;

CREATE INDEX idx_order_items_event_id ON order_items (event_id);


--
-- Name: idx_order_items_order_id;

CREATE INDEX idx_order_items_order_id ON order_items (order_id);


--
-- Name: idx_order_items_ticket_category_id;

CREATE INDEX idx_order_items_ticket_category_id ON order_items (ticket_category_id);


--
-- Name: idx_orders_expires_at;

CREATE INDEX idx_orders_expires_at ON orders (expires_at);


--
-- Name: idx_orders_status;

CREATE INDEX idx_orders_status ON orders (status);


--
-- Name: idx_orders_user_id;

CREATE INDEX idx_orders_user_id ON orders (user_id);
