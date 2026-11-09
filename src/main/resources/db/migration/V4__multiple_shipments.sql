-- PostgreSQL's default name for the original unique(order_id) constraint.
ALTER TABLE shipments DROP CONSTRAINT IF EXISTS shipments_order_id_key;
CREATE INDEX v1_shipment_order ON shipments(order_id,id);
