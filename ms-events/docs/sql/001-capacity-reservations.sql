CREATE TABLE IF NOT EXISTS capacity_reservations (
    order_item_id uuid PRIMARY KEY,
    event_id uuid NOT NULL,
    ticket_category_id uuid NOT NULL,
    quantity integer NOT NULL CHECK (quantity > 0),
    status varchar(16) NOT NULL CHECK (status IN ('RESERVED', 'REJECTED', 'RELEASED'))
);
