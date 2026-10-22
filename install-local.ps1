<#
Creates and loads the Fraud Detection demonstration database on a local PostgreSQL 18 installation.

Default behaviour is safe:
  - creates fraud_detection_demo only when it does not already exist;
  - never overwrites an existing database;
  - prompts locally for the PostgreSQL password, without placing it in this file.

To deliberately reload the deterministic demo data in an existing database:
  .\install-local.ps1 -ResetDemoData
#>

[CmdletBinding()]
param(
    [string]$HostName = 'localhost',
    [int]$Port = 5432,
    [string]$AdminUser = 'postgres',
    [string]$DatabaseName = 'fraud_detection_demo',
    [switch]$ResetDemoData
)

$ErrorActionPreference = 'Stop'

if ($DatabaseName -notmatch '^[A-Za-z_][A-Za-z0-9_]{0,62}$') {
    throw 'DatabaseName must begin with a letter or underscore and contain only letters, digits, or underscores.'
}

$pgBin = 'C:\Program Files\PostgreSQL\18\bin'
$psql = Join-Path $pgBin 'psql.exe'
$createdb = Join-Path $pgBin 'createdb.exe'
$schemaFile = Join-Path $PSScriptRoot 'schema.sql'
$seedFile = Join-Path $PSScriptRoot 'seed.sql'

foreach ($requiredFile in @($psql, $createdb, $schemaFile, $seedFile)) {
    if (-not (Test-Path -LiteralPath $requiredFile)) {
        throw "Required file was not found: $requiredFile"
    }
}

# The password is requested only in the local PowerShell session. PGPASSWORD is
# removed before the script exits and is never written to disk by this script.
$securePassword = Read-Host "Password for PostgreSQL user '$AdminUser'" -AsSecureString
$passwordPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePassword)

try {
    $env:PGPASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($passwordPointer)

    $existing = & $psql -X -A -t -q -w -h $HostName -p $Port -U $AdminUser -d postgres `
        -v ON_ERROR_STOP=1 -c "SELECT 1 FROM pg_database WHERE datname = '$DatabaseName';"
    if ($LASTEXITCODE -ne 0) {
        throw 'Could not connect to PostgreSQL. Check the user name, password, host, port, and that the service is running.'
    }

    # When the database is absent, psql returns no output and PowerShell stores it
    # as $null. Array containment safely handles both a null result and normal text.
    $databaseExists = @($existing) -contains '1'
    $isNewDatabase = -not $databaseExists
    if ($isNewDatabase) {
        Write-Host "Creating database '$DatabaseName'..."
        & $createdb -w -h $HostName -p $Port -U $AdminUser $DatabaseName
        if ($LASTEXITCODE -ne 0) { throw "Failed to create database '$DatabaseName'." }
    } else {
        Write-Host "Database '$DatabaseName' already exists."
        if (-not $ResetDemoData) {
            Write-Host 'No changes were made. Re-run with -ResetDemoData only if you want to reload the supplied demo data.'
            return
        }

        $confirmation = Read-Host "Reloading demo data will replace rows in the fraud_detection schema of '$DatabaseName'. Type RESET to continue"
        if ($confirmation -cne 'RESET') {
            Write-Host 'Cancelled. No changes were made.'
            return
        }
    }

    Write-Host 'Applying schema.sql...'
    & $psql -X -w -h $HostName -p $Port -U $AdminUser -d $DatabaseName -v ON_ERROR_STOP=1 -f $schemaFile
    if ($LASTEXITCODE -ne 0) { throw 'schema.sql failed; the seed data was not loaded.' }

    Write-Host 'Loading seed.sql...'
    & $psql -X -w -h $HostName -p $Port -U $AdminUser -d $DatabaseName -v ON_ERROR_STOP=1 -f $seedFile
    if ($LASTEXITCODE -ne 0) { throw 'seed.sql failed.' }

    Write-Host 'Verifying installed data...'
    & $psql -X -w -h $HostName -p $Port -U $AdminUser -d $DatabaseName -v ON_ERROR_STOP=1 -c @'
SET search_path TO fraud_detection;
SELECT
    (SELECT COUNT(*) FROM payment_transaction) AS transactions,
    (SELECT COUNT(*) FROM risk_assessment) AS assessments,
    (SELECT COUNT(*) FROM fraud_alert) AS alerts,
    (SELECT COUNT(*) FROM rule_match) AS rule_matches;
'@
    if ($LASTEXITCODE -ne 0) { throw 'The database was created, but the verification query failed.' }

    Write-Host "Success. Database '$DatabaseName' is ready for the Spring Boot application."
}
finally {
    if ($passwordPointer -ne [IntPtr]::Zero) {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($passwordPointer)
    }
    Remove-Item Env:\PGPASSWORD -ErrorAction SilentlyContinue
}
