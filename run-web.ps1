# Start the Angular dev server (http://localhost:4200)
Set-Location "$PSScriptRoot\web"
if (-not (Test-Path "node_modules")) { npm install }
npm start
