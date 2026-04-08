#!/bin/bash

# Read organization from file
ORG_NAME=$(cat org_name.txt)
SUBJ_BASE="/C=US/ST=California/L=SanFrancisco/O=$ORG_NAME"

# 1. Root CA
# Generate traditional EC key, then convert to PKCS#8
openssl ecparam -name prime256v1 -genkey -noout -out ca.key.tmp
openssl pkcs8 -topk8 -inform PEM -outform PEM -nocrypt -in ca.key.tmp -out ca.key
rm ca.key.tmp

openssl req -x509 -new -nodes -key ca.key -sha256 -days 3650 \
    -subj "$SUBJ_BASE/CN=RootCA" -out ca.crt

# 2. Intermediate CA
openssl ecparam -name prime256v1 -genkey -noout -out intermediate.key.tmp
openssl pkcs8 -topk8 -inform PEM -outform PEM -nocrypt -in intermediate.key.tmp -out intermediate.key
rm intermediate.key.tmp

openssl req -new -key intermediate.key \
    -subj "$SUBJ_BASE/CN=IntermediateCA" -out intermediate.csr

# Sign Intermediate
openssl x509 -req -in intermediate.csr -CA ca.crt -CAkey ca.key \
    -CAcreateserial -out intermediate.crt -days 1825 -sha256

# 3. Server Certificate
openssl ecparam -name prime256v1 -genkey -noout -out server.key.tmp
openssl pkcs8 -topk8 -inform PEM -outform PEM -nocrypt -in server.key.tmp -out server.key
rm server.key.tmp

openssl req -new -key server.key \
    -subj "$SUBJ_BASE/CN=localhost" -out server.csr

# Sign Server
openssl x509 -req -in server.csr -CA intermediate.crt -CAkey intermediate.key \
    -CAcreateserial -out server.crt -days 365 -sha256

echo "PKCS#8 ECDSA PKI Generation Complete."