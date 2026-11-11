#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE'
Usage: deploy.sh PROJECT REGION IMAGE RUNTIME_SERVICE_ACCOUNT TOKEN_SECRET VERSION [SERVICE]

Builds api/Dockerfile with Cloud Build, pushes IMAGE to an existing registry,
and deploys a public HTTPS Cloud Run service protected by the API Bearer token.
TOKEN_SECRET and VERSION identify an existing Secret Manager secret; they are
not secret values. SERVICE defaults to statecraft-api.
USAGE
}

if [[ ${1:-} == --help || ${1:-} == -h ]]; then
  usage
  exit 0
fi
if [[ $# -lt 6 || $# -gt 7 ]]; then
  usage >&2
  exit 2
fi

project=$1
region=$2
image=$3
runtime_service_account=$4
token_secret=$5
secret_version=$6
service=${7:-statecraft-api}

if [[ ! $secret_version =~ ^[1-9][0-9]*$ ]]; then
  printf '%s\n' 'VERSION must be a numeric secret version, not a secret value or latest.' >&2
  exit 2
fi
if [[ $image == *,* || $image == *[[:space:]]* || $token_secret == *[:,=]* ]]; then
  printf '%s\n' 'IMAGE and TOKEN_SECRET must be single provider resource references.' >&2
  exit 2
fi
command -v gcloud >/dev/null || {
  printf '%s\n' 'Install and configure the Google Cloud CLI before running this script.' >&2
  exit 1
}

script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
repo_root=$(cd -- "$script_dir/../.." && pwd)

# All parameters below are non-secret resource identifiers. No token is read,
# written into the build context, passed as an argument, or printed by this script.
gcloud builds submit "$repo_root" \
  --project="$project" \
  --region="$region" \
  --config="$script_dir/cloudbuild.yaml" \
  --ignore-file="$script_dir/gcloudignore" \
  --substitutions="_IMAGE=$image"

gcloud run deploy "$service" \
  --project="$project" \
  --region="$region" \
  --image="$image" \
  --service-account="$runtime_service_account" \
  --allow-unauthenticated \
  --port=8080 \
  --cpu=1 \
  --memory=1Gi \
  --concurrency=4 \
  --min-instances=0 \
  --max-instances=2 \
  --timeout=30s \
  --update-env-vars=STATECRAFT_MAX_CONCURRENT=4 \
  --update-secrets="STATECRAFT_API_TOKEN=$token_secret:$secret_version"
