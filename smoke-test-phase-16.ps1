# =============================================================================
# FITT Phase 16 Smoke Test
# Self-contained: registers account if needed, completes onboarding, then
# verifies the full workout session lifecycle end to end.
# =============================================================================

$BASE     = 'http://localhost:8080'
$EMAIL    = 'smoketest2@fitt.com'
$PASSWORD = 'FittSmoke2026!'
$CT       = 'application/json'

$passed = 0
$failed = 0

function Pass($msg) {
    Write-Host "[PASS] $msg" -ForegroundColor Green
    $script:passed++
}

function Fail($msg) {
    Write-Host "[FAIL] $msg" -ForegroundColor Red
    $script:failed++
}

function Step($msg) {
    Write-Host ""
    Write-Host "--- $msg ---" -ForegroundColor Cyan
}

function Post($path, $body, $hdrs) {
    $json = $body | ConvertTo-Json -Depth 5
    if ($hdrs) {
        return Invoke-RestMethod -Uri "$BASE$path" -Method Post `
            -ContentType $CT -Body $json -Headers $hdrs
    } else {
        return Invoke-RestMethod -Uri "$BASE$path" -Method Post `
            -ContentType $CT -Body $json
    }
}

function Get-Resource($path, $hdrs) {
    return Invoke-RestMethod -Uri "$BASE$path" -Headers $hdrs
}

# Decode the uid claim out of the JWT payload segment.
# JWT is three base64url segments separated by dots.
# The payload (middle segment) is base64url encoded JSON.
function Get-UserId($token) {
    $seg = $token.Split('.')[1]
    $seg = $seg -replace '-', '+' -replace '_', '/'
    switch ($seg.Length % 4) {
        2 { $seg += '==' }
        3 { $seg += '='  }
    }
    $bytes   = [System.Convert]::FromBase64String($seg)
    $payload = [System.Text.Encoding]::UTF8.GetString($bytes)
    $claims  = $payload | ConvertFrom-Json
    return [int]$claims.uid
}

# =============================================================================
# STEP 1 - Get a token: try register first, fall back to login if exists
# =============================================================================
Step "1. Authenticate (register or login)"

$token = $null

try {
    $res   = Post '/api/identity/auth/register' @{ email = $EMAIL; password = $PASSWORD }
    $token = $res.token
    Pass "Registered new account and received token"
} catch {
    try {
        $res   = Post '/api/identity/auth/login' @{ email = $EMAIL; password = $PASSWORD }
        $token = $res.token
        Pass "Account exists, logged in successfully"
    } catch {
        Fail "Both register and login failed: $_"
        exit 1
    }
}

$userId  = Get-UserId $token
$headers = @{ Authorization = "Bearer $token" }
Write-Host "    userId=$userId"

# =============================================================================
# STEP 2 - Save biometrics (upsert - safe to call every run)
# =============================================================================
Step "2. Save biometrics"

try {
    Post "/api/users/$userId/identity/onboarding/biometrics" @{
        heightCm        = 178
        weightKg        = 80
        dateOfBirth     = '1990-01-15'
        biologicalSex   = 'MALE'
        activityLevel   = 'MODERATELY_ACTIVE'
        unitsPreference = 'METRIC'
    } $headers | Out-Null
    Pass "Biometrics saved"
} catch {
    Fail "Save biometrics failed: $_"
    exit 1
}

# =============================================================================
# STEP 3 - Save goals (upsert - safe to call every run)
# =============================================================================
Step "3. Save goals"

try {
    Post "/api/users/$userId/identity/onboarding/goals" @{
        goalDirection   = 'MAINTAIN'
        targetWeightKg  = $null
        timelineWeeks   = $null
        calorieGoalMode = 'AUTO'
        calorieGoal     = $null
    } $headers | Out-Null
    Pass "Goals saved"
} catch {
    Fail "Save goals failed: $_"
    exit 1
}

# =============================================================================
# STEP 4 - List exercises (confirm library is seeded)
# =============================================================================
Step "4. List exercises"

try {
    $exercises = Get-Resource "/api/users/$userId/exercises" $headers

    if ($exercises.Count -gt 0) {
        Pass "Exercise library has $($exercises.Count) exercises"
        $exerciseId   = $exercises[0].id
        $exerciseName = $exercises[0].name
        Write-Host "    Using: $exerciseName (id $exerciseId)"
    } else {
        Fail "Exercise library is empty - seed exercises before running"
        exit 1
    }
} catch {
    Fail "List exercises failed: $_"
    exit 1
}

# =============================================================================
# STEP 5 - Create a 2-day workout plan
# =============================================================================
Step "5. Create workout plan"

try {
    $plan = Post "/api/users/$userId/workout-plans" @{
        name        = 'Smoke Test Plan'
        daysPerWeek = 2
        splitType   = 'CUSTOM'
        days        = @(
            @{
                orderIndex = 0
                dayLabel   = 'Day A'
                exercises  = @(
                    @{ exerciseId = $exerciseId; sets = 3; reps = 8 }
                )
            },
            @{
                orderIndex = 1
                dayLabel   = 'Day B'
                exercises  = @(
                    @{ exerciseId = $exerciseId; sets = 4; reps = 10 }
                )
            }
        )
    } $headers

    if ($plan.id) {
        Pass "Plan created id=$($plan.id)"
        $planId = $plan.id
    } else {
        Fail "Plan response missing id"
        exit 1
    }
} catch {
    Fail "Create plan failed: $_"
    exit 1
}

# =============================================================================
# STEP 6 - List plans and confirm created plan appears
# =============================================================================
Step "6. List plans"

try {
    $plans = Get-Resource "/api/users/$userId/workout-plans" $headers
    $found = $plans | Where-Object { $_.id -eq $planId }

    if ($found) {
        Pass "Created plan id=$planId appears in list"
    } else {
        Fail "Created plan id=$planId not found in list"
    }
} catch {
    Fail "List plans failed: $_"
}

# =============================================================================
# STEP 7 - Activate the plan
# =============================================================================
Step "7. Activate plan"

try {
    $activated = Invoke-RestMethod `
        -Uri "$BASE/api/users/$userId/workout-plans/$planId/activate" `
        -Method Post -Headers $headers -ContentType $CT -Body '{}'

    if ($activated.active -eq $true) {
        Pass "Plan $planId is now active"
    } else {
        Fail "Activate response did not show active=true"
    }
} catch {
    Fail "Activate plan failed: $_"
}

# =============================================================================
# STEP 8 - Count completed sessions for this plan before any sessions
# =============================================================================
Step "8. Count completed sessions (baseline)"

try {
    $countBefore = Get-Resource `
        "/api/users/$userId/workout-sessions/count?planId=$planId" $headers
    Pass "Baseline count is $countBefore"
} catch {
    Fail "Count sessions failed: $_"
    $countBefore = 0
}

# =============================================================================
# STEP 9 - Create a session with planId
# =============================================================================
Step "9. Create session with planId"

try {
    $startedAt = (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ssZ")

    $session = Post "/api/users/$userId/workout-sessions" @{
        startedAt = $startedAt
        planId    = $planId
    } $headers

    if ($session.id -and $session.planId -eq $planId) {
        Pass "Session created id=$($session.id) planId=$($session.planId)"
        $sessionId = $session.id
    } else {
        Fail "Session response missing id or planId mismatch"
        exit 1
    }
} catch {
    Fail "Create session failed: $_"
    exit 1
}

# =============================================================================
# STEP 10 - Add an exercise entry to the session
# =============================================================================
Step "10. Add exercise entry"

try {
    $sessionAfterEntry = Post `
        "/api/users/$userId/workout-sessions/$sessionId/entries" `
        @{ exerciseId = $exerciseId } $headers

    $entry = $sessionAfterEntry.exerciseEntries |
        Where-Object { $_.exercise.id -eq $exerciseId }

    if ($entry) {
        Pass "Exercise entry added entryId=$($entry.id)"
        $entryId = $entry.id
    } else {
        Fail "Exercise entry not found in session after add"
        exit 1
    }
} catch {
    Fail "Add exercise entry failed: $_"
    exit 1
}

# =============================================================================
# STEP 11 - Log a set and confirm completed=true
# =============================================================================
Step "11. Log a set"

try {
    $sessionAfterSet = Post `
        "/api/users/$userId/workout-sessions/$sessionId/entries/$entryId/sets" `
        @{ reps = 8; weight = 100 } $headers

    $updatedEntry = $sessionAfterSet.exerciseEntries |
        Where-Object { $_.id -eq $entryId }
    $loggedSet = $updatedEntry.sets | Where-Object { $_.completed -eq $true }

    if ($loggedSet) {
        Pass "Set logged completed=true reps=$($loggedSet.reps) weight=$($loggedSet.weight)"
    } else {
        Fail "No completed=true set found after logSet"
    }
} catch {
    Fail "Log set failed: $_"
}

# =============================================================================
# STEP 12 - Finish the session
# =============================================================================
Step "12. Finish session"

try {
    $finished = Invoke-RestMethod `
        -Uri "$BASE/api/users/$userId/workout-sessions/$sessionId/finish" `
        -Method Post -Headers $headers -ContentType $CT -Body '{}'

    if ($finished.finishedAt) {
        Pass "Session finished at $($finished.finishedAt)"
    } else {
        Fail "finishedAt is null after finish"
    }
} catch {
    Fail "Finish session failed: $_"
}

# =============================================================================
# STEP 13 - Count completed sessions (expect countBefore + 1)
# =============================================================================
Step "13. Count after finish (expect $($countBefore + 1))"

try {
    $countAfter = Get-Resource `
        "/api/users/$userId/workout-sessions/count?planId=$planId" $headers
    $expected = $countBefore + 1

    if ($countAfter -eq $expected) {
        Pass "Count is now $countAfter (was $countBefore)"
    } else {
        Fail "Expected $expected got $countAfter"
    }
} catch {
    Fail "Count after finish failed: $_"
}

# =============================================================================
# STEP 14 - Day rotation: verify next day index and start a second session
# count modulo dayCount gives the next day index.
# 1 completed session, 2-day plan: 1 mod 2 = 1 (Day B).
# =============================================================================
Step "14. Day rotation and second session"

try {
    $count    = Get-Resource `
        "/api/users/$userId/workout-sessions/count?planId=$planId" $headers
    $dayCount = $plan.days.Count
    $nextIdx  = $count % $dayCount
    $nextDay  = $plan.days | Where-Object { $_.orderIndex -eq $nextIdx }

    if ($nextDay) {
        Pass "After $count session(s): next day index=$nextIdx label='$($nextDay.dayLabel)'"
    } else {
        Fail "Could not resolve day from count=$count dayCount=$dayCount"
    }

    $startedAt2 = (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ssZ")
    $session2   = Post "/api/users/$userId/workout-sessions" @{
        startedAt = $startedAt2
        planId    = $planId
    } $headers

    if ($session2.id -and $session2.planId -eq $planId) {
        Pass "Second session created id=$($session2.id) - day rotation confirmed"
    } else {
        Fail "Second session creation failed"
    }
} catch {
    Fail "Day rotation step failed: $_"
}

# =============================================================================
# Summary
# =============================================================================
Write-Host ""
Write-Host "=================================" -ForegroundColor White
Write-Host " PASSED: $passed   FAILED: $failed" -ForegroundColor White
Write-Host "=================================" -ForegroundColor White

if ($failed -gt 0) { exit 1 } else { exit 0 }
