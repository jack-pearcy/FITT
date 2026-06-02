-- V1 baseline. The full FITT schema as of the introduction of Flyway.
-- Generated from the JPA entities, then given readable constraint names
-- and an explicit utf8mb4 charset so the schema does not depend on server
-- defaults. Every change after this point is a new versioned migration.

-- Identity context

create table users (
                       id bigint not null auto_increment,
                       email varchar(255) not null,
                       password_hash varchar(255) not null,
                       primary key (id),
                       constraint uk_users_email unique (email)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_0900_ai_ci;

create table user_biometrics (
                                 id bigint not null auto_increment,
                                 owner_user_id bigint not null,
                                 height_cm decimal(5,2) not null,
                                 weight_kg decimal(5,2) not null,
                                 date_of_birth date not null,
                                 biological_sex enum ('FEMALE','MALE') not null,
                                 activity_level enum ('ACTIVE','LIGHTLY_ACTIVE','MODERATELY_ACTIVE','SEDENTARY','VERY_ACTIVE') not null,
                                 units_preference enum ('IMPERIAL','METRIC') not null,
                                 created_at datetime(6) not null,
                                 updated_at datetime(6) not null,
                                 primary key (id),
                                 constraint uk_user_biometrics_owner unique (owner_user_id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_0900_ai_ci;

create table user_goals (
                            id bigint not null auto_increment,
                            owner_user_id bigint not null,
                            goal_direction enum ('BULK','CUT','MAINTAIN') not null,
                            target_weight_kg decimal(5,2),
                            timeline_weeks integer,
                            calorie_goal_mode enum ('AUTO','MANUAL') not null,
                            calorie_goal integer,
                            created_at datetime(6) not null,
                            updated_at datetime(6) not null,
                            primary key (id),
                            constraint uk_user_goals_owner unique (owner_user_id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_0900_ai_ci;

-- Workouts context

create table exercises (
                           id bigint not null auto_increment,
                           owner_user_id bigint,
                           name varchar(255) not null,
                           muscle_group varchar(255) not null,
                           is_custom bit not null,
                           primary key (id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_0900_ai_ci;

create table workout_plans (
                               id bigint not null auto_increment,
                               owner_user_id bigint not null,
                               name varchar(255) not null,
                               split_type varchar(255) not null,
                               days_per_week integer not null,
                               is_active bit not null,
                               primary key (id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_0900_ai_ci;

create table workout_plan_days (
                                   id bigint not null auto_increment,
                                   workout_plan_id bigint not null,
                                   day_label varchar(255) not null,
                                   order_index integer not null,
                                   primary key (id),
                                   constraint fk_plan_days_plan foreign key (workout_plan_id) references workout_plans (id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_0900_ai_ci;

create table plan_day_exercises (
                                    id bigint not null auto_increment,
                                    workout_plan_day_id bigint not null,
                                    exercise_id bigint not null,
                                    sets integer not null,
                                    reps integer not null,
                                    order_index integer not null,
                                    primary key (id),
                                    constraint fk_plan_day_exercises_day foreign key (workout_plan_day_id) references workout_plan_days (id),
                                    constraint fk_plan_day_exercises_exercise foreign key (exercise_id) references exercises (id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_0900_ai_ci;

create table workout_sessions (
                                  id bigint not null auto_increment,
                                  owner_user_id bigint not null,
                                  plan_id bigint,
                                  started_at datetime(6) not null,
                                  finished_at datetime(6),
                                  notes TEXT,
                                  primary key (id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_0900_ai_ci;

create table exercise_entries (
                                  id bigint not null auto_increment,
                                  workout_session_id bigint not null,
                                  exercise_id bigint not null,
                                  order_index integer not null,
                                  primary key (id),
                                  constraint fk_exercise_entries_session foreign key (workout_session_id) references workout_sessions (id),
                                  constraint fk_exercise_entries_exercise foreign key (exercise_id) references exercises (id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_0900_ai_ci;

create table set_entries (
                             id bigint not null auto_increment,
                             exercise_entry_id bigint not null,
                             set_number integer not null,
                             reps integer,
                             weight decimal(6,2),
                             rpe decimal(3,1),
                             completed bit not null,
                             primary key (id),
                             constraint fk_set_entries_exercise_entry foreign key (exercise_entry_id) references exercise_entries (id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_0900_ai_ci;

create table user_progression_preferences (
                                              id bigint not null auto_increment,
                                              owner_user_id bigint not null,
                                              progression_model enum ('DOUBLE_PROGRESSION','LINEAR_PERIODIZATION','RPE_AUTOREGULATION') not null,
                                              primary key (id),
                                              constraint uk_user_progression_preferences_owner unique (owner_user_id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_0900_ai_ci;

-- Measurements context

create table body_weight_entries (
                                     id bigint not null auto_increment,
                                     owner_user_id bigint not null,
                                     logged_at date not null,
                                     weight decimal(5,1) not null,
                                     created_at datetime(6) not null,
                                     primary key (id),
                                     constraint uk_body_weight_user_logged_at unique (owner_user_id, logged_at)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_0900_ai_ci;