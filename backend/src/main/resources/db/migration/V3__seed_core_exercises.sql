-- V3 seeds the core and ab exercises that the V2 library skipped.
-- Same as V2, null owner and is_custom 0 mean these are global and ship with the app.

insert into exercises (owner_user_id, name, muscle_group, is_custom)
values (null, 'Plank', 'CORE', 0),
       (null, 'Side Plank', 'CORE', 0),
       (null, 'Dead Bug', 'CORE', 0),
       (null, 'Pallof Press', 'CORE', 0),
       (null, 'Ab Wheel Rollout', 'CORE', 0),
       (null, 'Hanging Leg Raise', 'CORE', 0),
       (null, 'Hanging Knee Raise', 'CORE', 0),
       (null, 'Cable Crunch', 'CORE', 0),
       (null, 'Bicycle Crunch', 'CORE', 0);