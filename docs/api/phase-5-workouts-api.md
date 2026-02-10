# Phase 5: Workouts API Contract v1

This file is the contract for the Workouts endpoints. Controllers must match this behavior exactly so the frontend can integrate without guessing. Internals can evolve later, but this contract stays stable unless I explicitly version it.

## Auth and ownership rules

- All `/api/workouts/**` endpoints require authentication.
- Ownership-safe behavior is mandatory:
- If a workout does not exist OR it exists but belongs to a different user, return `404 Not Found`.
- Do not return `403` for “not owned” because that confirms the resource exists.

## Content types

- Requests: `Content-Type: application/json`
- Responses: `Content-Type: application/json` (except 204)

## Public DTOs

CreateWorkoutRequest
- `name` string, required, must not be blank

WorkoutResponse
- `id` integer
- `name` string
- `createdAt` ISO-8601 timestamp string
- `updatedAt` ISO-8601 timestamp string

Error format (Problem Details, RFC 9457 style)
- We return one consistent error shape everywhere.
- Fields:
- `type` string (URI), optional but preferred
- `title` string
- `status` integer
- `detail` string
- `instance` string (URI), optional

## Endpoints

### POST /api/workouts

What it does
- Creates a workout owned by the authenticated user.

Request body
- CreateWorkoutRequest

Example body
- `{ "name": "Push Day" }`

Success
- `201 Created`
- Response body is the created WorkoutResponse

Validation errors
- `400 Bad Request`
- Problem Details payload (example cases: missing name, blank name)

### GET /api/workouts/{workoutId}

What it does
- Returns one workout by id, but only if it belongs to the authenticated user.

Path param
- `workoutId` integer, required

Success
- `200 OK`
- WorkoutResponse

Not found / not owned
- `404 Not Found` (ownership-safe)

### DELETE /api/workouts/{workoutId}

What it does
- Deletes one workout by id, but only if it belongs to the authenticated user.

Path param
- `workoutId` integer, required

Success
- `204 No Content`
- No response body

Not found / not owned
- `404 Not Found` (ownership-safe)

## Implementation notes (non-negotiable)

- Controllers do not return entities directly.
- Controllers map request DTO -> service call -> response DTO.
- Errors are uniform Problem Details (no random `{ "error": "..." }` shapes).
