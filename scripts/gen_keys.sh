#!/usr/bin/env bash
# Generates the RSA key pair used to sign (private) and verify (public) test JWTs.
# Keys are written to keys/, which is git-ignored. Never commit them.
set -euo pipefail

mkdir -p keys
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out keys/private.pem
openssl rsa -in keys/private.pem -pubout -out keys/public.pem
chmod 600 keys/private.pem
echo "Created keys/private.pem and keys/public.pem"
