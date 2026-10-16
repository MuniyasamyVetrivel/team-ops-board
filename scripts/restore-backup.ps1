<#
.SYNOPSIS
  Restores a Team Ops Board backup (.tobk) into a MySQL database you name, after checking its checksum.

.DESCRIPTION
  1. Verifies the SHA-256 checksum (the .sha256 file next to the backup, or -Checksum from the backup history).
  2. Decrypts the backup with BACKUP_ENCRYPTION_KEY (environment variable, else backend\.env, else a prompt).
  3. Creates the database and loads database.sql into it. An existing database with tables is refused unless you
     pass -Replace; restoring over the live database also asks you to type its name.
  4. Copies the uploaded files to -UploadsTo (default: a new folder next to the backup).
  5. Records BACKUP_RESTORED in the audit log of the app database (DB_URL's database, or -AuditDatabase; -NoAudit
     skips it).

  Needs a JDK 21 (java) and the MySQL client (mysql.exe). See docs/backup-and-restore.md.

.EXAMPLE
  .\scripts\restore-backup.ps1 -BackupFile D:\team-ops-backups\team-ops-backup-20261016T203000Z.tobk -Database team_ops_board_restore_test
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)] [string] $BackupFile,
    [Parameter(Mandatory = $true)] [string] $Database,
    [string] $Checksum = '',
    [string] $EnvFile = (Join-Path $PSScriptRoot '..\backend\.env'),
    [string] $DbHost = '',
    [int] $Port = 0,
    [string] $User = '',
    [string] $MySql = '',
    [string] $Java = '',
    [string] $UploadsTo = '',
    [string] $AuditDatabase = '',
    [switch] $NoAudit,
    [switch] $Replace
)

$ErrorActionPreference = 'Stop'

function Read-EnvFile([string] $path) {
    $values = @{}
    if (Test-Path -LiteralPath $path) {
        foreach ($line in Get-Content -LiteralPath $path -Encoding UTF8) {
            if ($line -match '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=(.*)$') {
                $values[$Matches[1]] = $Matches[2].Trim()
            }
        }
    }
    return $values
}

function Quote-Option([string] $value) {
    return '"' + $value.Replace('\', '\\').Replace('"', '\"') + '"'
}

function Sql-Text([string] $value) {
    return "'" + $value.Replace('\', '\\').Replace("'", "''") + "'"
}

if ($Database -notmatch '^[A-Za-z0-9_]{1,64}$') { throw "Database name may only contain letters, digits and _" }
$BackupFile = (Resolve-Path -LiteralPath $BackupFile).Path
$envValues = Read-EnvFile $EnvFile

# --- Connection: parameters, else DB_URL / DB_USERNAME / DB_PASSWORD from backend\.env ---
$appDatabase = ''
$urlHost = 'localhost'; $urlPort = 3306
if ($envValues['DB_URL'] -match '^jdbc:mysql://([^:/?]+)(?::(\d+))?/([A-Za-z0-9_$-]+)') {
    $urlHost = $Matches[1]; if ($Matches[2]) { $urlPort = [int]$Matches[2] }; $appDatabase = $Matches[3]
}
if (-not $DbHost) { $DbHost = $urlHost }
if ($Port -le 0) { $Port = $urlPort }
if (-not $User) { $User = $envValues['DB_USERNAME'] }
if (-not $User) { $User = Read-Host 'MySQL user' }
$password = $env:MYSQL_PASSWORD
if (-not $password -and $User -eq $envValues['DB_USERNAME']) { $password = $envValues['DB_PASSWORD'] }
if (-not $password) {
    $secure = Read-Host "MySQL password for $User" -AsSecureString
    $password = [Runtime.InteropServices.Marshal]::PtrToStringAuto([Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure))
}
if (-not $AuditDatabase -and -not $NoAudit) { $AuditDatabase = $appDatabase }
if ($NoAudit) { $AuditDatabase = '' }

# --- Tools ---
if (-not $Java) { $Java = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin\java.exe' } else { 'java' } }
if (-not $MySql) {
    $default = 'C:\Program Files\MySQL\MySQL Server 8.4\bin\mysql.exe'
    $MySql = if (Test-Path -LiteralPath $default) { $default } else { 'mysql' }
}
$tool = Join-Path $PSScriptRoot 'BackupTool.java'

# --- Key: environment, else backend\.env, else prompt. Passed to Java through the environment only. ---
$key = $env:BACKUP_ENCRYPTION_KEY
if (-not $key) { $key = $envValues['BACKUP_ENCRYPTION_KEY'] }
if (-not $key) {
    $secureKey = Read-Host 'BACKUP_ENCRYPTION_KEY' -AsSecureString
    $key = [Runtime.InteropServices.Marshal]::PtrToStringAuto([Runtime.InteropServices.Marshal]::SecureStringToBSTR($secureKey))
}

$work = Join-Path ([IO.Path]::GetTempPath()) ('tob-restore-' + [Guid]::NewGuid().ToString('N'))
$options = Join-Path ([IO.Path]::GetTempPath()) ('tob-restore-' + [Guid]::NewGuid().ToString('N') + '.cnf')
$previousKey = $env:BACKUP_ENCRYPTION_KEY
try {
    # MySQL credentials in a temporary option file, not on the command line.
    $cnf = "[client]`nhost=$(Quote-Option $DbHost)`nport=$Port`nuser=$(Quote-Option $User)`npassword=$(Quote-Option $password)`ndefault-character-set=utf8mb4`n"
    [IO.File]::WriteAllText($options, $cnf, (New-Object Text.UTF8Encoding $false))

    function Invoke-MySql([string[]] $arguments) {
        # Windows PowerShell turns redirected stderr lines into errors; collect them as text instead.
        $ErrorActionPreference = 'Continue'
        $output = & $MySql "--defaults-extra-file=$options" @arguments 2>&1 | ForEach-Object { "$_" }
        if ($LASTEXITCODE -ne 0) { throw "mysql failed: $($output -join ' ')" }
        return $output | Where-Object { $_ -notmatch '^mysql: \[Warning\]' }
    }

    Write-Host "1/5 Verifying checksum and decrypting $([IO.Path]::GetFileName($BackupFile))"
    $env:BACKUP_ENCRYPTION_KEY = $key
    $toolArgs = @($tool, 'extract', $BackupFile, $work)
    if ($Checksum) { $toolArgs += $Checksum }
    & $Java @toolArgs
    if ($LASTEXITCODE -ne 0) { throw 'The backup could not be verified or decrypted (see the error above)' }
    $env:BACKUP_ENCRYPTION_KEY = $previousKey
    $checksumUsed = if ($Checksum) { $Checksum } else { (Get-Content -LiteralPath "$BackupFile.sha256" -TotalCount 1).Split(' ')[0] }

    Write-Host "2/5 Preparing database $Database on ${DbHost}:$Port"
    $tables = [int]((Invoke-MySql @('-N', '-B', '-e', "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = '$Database'")) | Select-Object -Last 1)
    $exists = [int]((Invoke-MySql @('-N', '-B', '-e', "SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name = '$Database'")) | Select-Object -Last 1)
    if ($tables -gt 0 -and -not $Replace) {
        throw "Database $Database already has $tables tables. Choose another name, or pass -Replace to drop and replace it."
    }
    if ($appDatabase -and $Database -eq $appDatabase) {
        Write-Warning "$Database is the app's own database (DB_URL). Its current data will be replaced by the backup."
        $typed = Read-Host "Type the database name ($Database) to continue"
        if ($typed -ne $Database) { throw 'Restore cancelled' }
    }
    try {
        if ($exists -gt 0 -and $Replace) { Invoke-MySql @('-e', "DROP DATABASE ``$Database``") | Out-Null }
        Invoke-MySql @('-e', "CREATE DATABASE IF NOT EXISTS ``$Database`` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci") | Out-Null
    }
    catch {
        if ("$_" -match 'Access denied') {
            throw "$_`nThe MySQL user $User may not create or drop $Database. Run again with -User root (or another account allowed to create databases)."
        }
        throw
    }

    Write-Host "3/5 Loading database.sql (this can take a while)"
    $dump = (Join-Path $work 'database.sql').Replace('\', '/')
    Invoke-MySql @($Database, '-e', "source $dump") | Out-Null
    $restoredTables = [int]((Invoke-MySql @('-N', '-B', '-e', "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = '$Database'")) | Select-Object -Last 1)

    Write-Host "4/5 Copying uploaded files"
    if (-not $UploadsTo) { $UploadsTo = Join-Path (Split-Path -Parent $BackupFile) "restore-$Database-uploads" }
    $uploads = Join-Path $work 'uploads'
    $fileCount = 0
    if (Test-Path -LiteralPath $uploads) {
        if ((Test-Path -LiteralPath $UploadsTo) -and (Get-ChildItem -LiteralPath $UploadsTo -Force | Select-Object -First 1)) {
            throw "Uploads folder $UploadsTo is not empty; pass an empty or new folder with -UploadsTo"
        }
        New-Item -ItemType Directory -Force -Path $UploadsTo | Out-Null
        Copy-Item -Path (Join-Path $uploads '*') -Destination $UploadsTo -Recurse
        $fileCount = (Get-ChildItem -LiteralPath $UploadsTo -Recurse -File).Count
    }

    Write-Host "5/5 Recording the restore in the audit log"
    if (-not $AuditDatabase) {
        Write-Warning 'No audit database (-NoAudit, or no DB_URL in backend\.env): the restore is not recorded.'
    }
    else {
        $details = "JSON_OBJECT('file', $(Sql-Text ([IO.Path]::GetFileName($BackupFile))), 'checksum', $(Sql-Text $checksumUsed), " +
            "'database', $(Sql-Text $Database), 'tables', $restoredTables, 'uploadFiles', $fileCount, " +
            "'uploadsFolder', $(Sql-Text $UploadsTo), 'osUser', $(Sql-Text $env:USERNAME), 'machine', $(Sql-Text $env:COMPUTERNAME), 'script', 'restore-backup.ps1')"
        try {
            Invoke-MySql @($AuditDatabase, '-e', "INSERT INTO audit_logs (action, entity_type, details, user_agent) VALUES ('BACKUP_RESTORED', 'BACKUP', $details, 'restore-backup.ps1')") | Out-Null
        }
        catch {
            Write-Warning "The restore worked but could not be recorded in $AuditDatabase.audit_logs: $_"
        }
    }

    Write-Host ''
    Write-Host "Restored $restoredTables tables into $Database and $fileCount files into $UploadsTo." -ForegroundColor Green
    Write-Host "To try it, point a copy of the app at it: DB_URL=...:$Port/$Database and FILE_STORAGE_DIR=$UploadsTo"
}
finally {
    $env:BACKUP_ENCRYPTION_KEY = $previousKey
    Remove-Item -LiteralPath $options -Force -ErrorAction SilentlyContinue
    Remove-Item -LiteralPath $work -Recurse -Force -ErrorAction SilentlyContinue
}
