param([Parameter(Mandatory=$true)][string]$RemoteUrl)
git remote remove origin 2>$null
git remote add origin $RemoteUrl
git branch -M main
git push -u origin main
git tag -f v1.0.0-rc8
git push -f origin v1.0.0-rc8
