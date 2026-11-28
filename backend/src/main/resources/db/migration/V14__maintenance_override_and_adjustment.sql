-- V14. The user's own maintenance calories and a signed daily adjustment.
--
-- maintenance_override_kcal replaces the estimated maintenance when the user
-- knows their own number. daily_adjustment_kcal is the surplus (above zero) or
-- deficit (below zero) the user chose, in place of the built-in bulk or cut
-- percentage.
--
-- Both columns are nullable. Rows saved before this migration keep nulls, and
-- a null keeps the calorie math as it was. One change per statement, as in V13.

alter table user_goals
    add column maintenance_override_kcal int null;

alter table user_goals
    add column daily_adjustment_kcal int null;
