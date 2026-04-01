# Phase 15 smoke test - backend API surface of the main shell
# Compatible with Windows PowerShell 5.1

$BASE     = "http://localhost:8080"
$EMAIL    = "smoketest2@fitt.com"
$PASSWORD = "FittSmoke2026!"

Write-Host ""
Write-Host "=============================="
Write-Host " FITT Phase 15 Smoke Tests"
Write-Host "=============================="

# Step 1 - login and capture the token
Write-Host ""
Write-Host "Step 1: Login"

$loginParams = @{
    Uri         = "$BASE/api/identity/auth/login"
    Method      = "POST"
    Body        = (@{ email = $EMAIL; password = $PASSWORD } | ConvertTo-Json)
    ContentType = "application/json"
}

try {
    $loginResponse = Invoke-RestMethod @loginParams

    $token = $loginResponse.token

    # Decode the uid claim from the JWT payload
    # JWT middle segment is base64 - pad it back out because PowerShell needs multiples of 4
    $payload = $token.Split('.')[1]
    $padded  = $payload.PadRight($payload.Length + (4 - $payload.Length % 4) % 4, '=')
    $decoded = [System.Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($padded))
    $claims  = $decoded | ConvertFrom-Json
    $userId  = $claims.uid

    Write-Host "  PASS - token received, userId = $userId"
} catch {
    Write-Host "  FAIL - login failed: $($_.Exception.Message)"
    Write-Host "  Cannot continue without a token. Stopping."
    exit 1
}

$authHeader = @{ Authorization = "Bearer $token" }

# Step 2 - 7-day average weight
Write-Host ""
Write-Host "Step 2: 7-day average weight (shell summary)"

$avgParams = @{
    Uri     = "$BASE/api/users/$userId/measurements/weight/average"
    Method  = "GET"
    Headers = $authHeader
}

try {
    $avgResponse = Invoke-RestMethod @avgParams

    # average is null if the user has no weight entries - both are valid responses
    if ($avgResponse.average -ne $null) {
        Write-Host "  PASS - average = $($avgResponse.average)"
    } else {
        Write-Host "  PASS - average = -- (no entries yet)"
    }
} catch {
    Write-Host "  FAIL - $($_.Exception.Message)"
}

# Step 3 - TDEE
Write-Host ""
Write-Host "Step 3: TDEE (shell summary calorie display)"

$tdeeParams = @{
    Uri     = "$BASE/api/users/$userId/identity/onboarding/tdee"
    Method  = "GET"
    Headers = $authHeader
}

try {
    $tdeeResponse = Invoke-RestMethod @tdeeParams
    Write-Host "  PASS - tdee = $($tdeeResponse.tdee)"
} catch {
    $status = $_.Exception.Response.StatusCode.value__
    # 400 = biometrics exist but calculation failed
    # 404 = no biometrics saved yet for this user
    # both are valid for a fresh account, shell shows "--"
    if ($status -eq 400 -or $status -eq 404) {
        Write-Host "  PASS - $status returned (no biometrics for this user), shell shows --"
    } else {
        Write-Host "  FAIL - unexpected status $status"
    }
}

# Step 4 - guard check on average endpoint
Write-Host ""
Write-Host "Step 4: Guard check - 7-day average without token should be 401"

$noAuthAvgParams = @{
    Uri    = "$BASE/api/users/$userId/measurements/weight/average"
    Method = "GET"
}

try {
    Invoke-RestMethod @noAuthAvgParams
    Write-Host "  FAIL - got 200 but expected 401 (endpoint is unprotected)"
} catch {
    $status = $_.Exception.Response.StatusCode.value__
    if ($status -eq 401) {
        Write-Host "  PASS - 401 returned as expected"
    } else {
        Write-Host "  FAIL - expected 401 but got $status"
    }
}

# Step 5 - guard check on TDEE endpoint
Write-Host ""
Write-Host "Step 5: Guard check - TDEE without token should be 401"

$noAuthTdeeParams = @{
    Uri    = "$BASE/api/users/$userId/identity/onboarding/tdee"
    Method = "GET"
}

try {
    Invoke-RestMethod @noAuthTdeeParams
    Write-Host "  FAIL - got 200 but expected 401 (endpoint is unprotected)"
} catch {
    $status = $_.Exception.Response.StatusCode.value__
    if ($status -eq 401) {
        Write-Host "  PASS - 401 returned as expected"
    } else {
        Write-Host "  FAIL - expected 401 but got $status"
    }
}

Write-Host ""
Write-Host "=============================="
Write-Host " API checks done"
Write-Host " Manual browser checks still needed:"
Write-Host "   /home redirects to /home/workouts"
Write-Host "   Tabs navigate and highlight correctly"
Write-Host "   Back/forward works between tabs"
Write-Host "   Refresh on /home/workouts stays there"
Write-Host "   /home without token redirects to /login"
Write-Host "=============================="