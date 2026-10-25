$ErrorActionPreference = 'Stop'
uv export --no-dev --no-emit-project --format requirements-txt --output-file src/requirements.txt --quiet
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
