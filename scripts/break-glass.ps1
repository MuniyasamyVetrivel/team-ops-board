<#
.SYNOPSIS
  Break-glass recovery on the server: create a new Super Admin, or reset a Super Admin's password.

.DESCRIPTION
  For when nobody can sign in as Super Admin (see "Break-glass access" in docs/backup-and-restore.md). Runs the
  backend jar once as a command-line tool, without the web server, against the database in backend\.env:
  - it asks you to type the email again and then the temporary password (never on the command line);
  - the password must be changed at the next sign-in, and every session of that account is signed out;
  - reset-password works only for Super Admins and re-enables a disabled account;
  - every use is recorded in the audit log as BREAK_GLASS_USED, with your Windows user and the machine name.
  Run it in an interactive terminal on the server itself. It needs a JDK/JRE 21 and the built backend jar.

.EXAMPLE
  .\scripts\break-glass.ps1 -Command reset-password -Email rakesh@example.com

.EXAMPLE
  .\scripts\break-glass.ps1 -Command create-super-admin -Email it.admin@example.com -FirstName IT -LastName Admin
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)] [ValidateSet('create-super-admin', 'reset-password')] [string] $Command,
    [Parameter(Mandatory = $true)] [string] $Email,
    [string] $FirstName = '',
    [string] $LastName = '',
    [string] $Department = '',
    [switch] $GeneratePassword,
    [string] $Jar = '',
    [string] $AppDir = (Join-Path $PSScriptRoot '..\backend'),
    [string] $Java = ''
)

$ErrorActionPreference = 'Stop'
$AppDir = (Resolve-Path -LiteralPath $AppDir).Path
if (-not $Jar) {
    $found = Get-ChildItem -Path (Join-Path $AppDir 'target') -Filter 'team-ops-board-backend-*.jar' -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -notlike '*.original' } | Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if (-not $found) { throw "No backend jar found in $AppDir\target. Build it first: backend\mvnw.cmd -DskipTests package" }
    $Jar = $found.FullName
}
if (-not $Java) { $Java = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin\java.exe' } else { 'java' } }

$arguments = @(
    '-jar', $Jar,
    # A one-off command-line run: no web server, no background jobs, quiet logs.
    '--spring.main.web-application-type=none', '--app.scheduling.enabled=false', '--spring.main.banner-mode=off',
    '--logging.level.root=WARN',
    "--break-glass=$Command", "--email=$Email"
)
if ($FirstName) { $arguments += "--first-name=$FirstName" }
if ($LastName) { $arguments += "--last-name=$LastName" }
if ($Department) { $arguments += "--department=$Department" }
if ($GeneratePassword) { $arguments += '--generate-password' }

# The app reads backend\.env from its working folder.
Push-Location -LiteralPath $AppDir
try {
    # Messages on stderr are the tool's own; Windows PowerShell must not turn them into script errors.
    $ErrorActionPreference = 'Continue'
    & $Java @arguments
    $code = $LASTEXITCODE
}
finally {
    Pop-Location
}
exit $code
