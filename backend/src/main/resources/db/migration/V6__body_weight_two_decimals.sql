-- V6. Body weight is stored in kilograms to two decimal places, not one.
-- A client that works in pounds converts to kilograms before it sends. With one
-- decimal place, 180.4 lb became 81.8 kg and came back as 180.3 lb. Set weights
-- (set_entries.weight) already keep two places.
-- Widening only: every existing value fits and none is changed.

alter table body_weight_entries modify weight decimal(6,2) not null;
