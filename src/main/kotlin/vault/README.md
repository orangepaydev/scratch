# Key and Certificate Details

This document provides details about the keys and certificates used in the Vault application. It includes information
about the types of keys, their purposes, and how they are managed within the application.

## Key and Cert details and storage

| Key/Certificate Type        | Purpose                                        | Used By                             | Store Path                                                                   |
|-----------------------------|------------------------------------------------|-------------------------------------|------------------------------------------------------------------------------|
| CA Certificate              | Used establish trust                           | [Engine]Validate the Cert chain     | [Vault]pki/rootCA${YYYYMMDD} <br> [SSM]/app-workflow-v5/license/CACert       |
| CA Private Key              | Used to sign certificates                      | [Vault]Sign the intermediate CSR    | [Vault]pki/rootCA${YYYYMMDD}                                                 |
| Intermediate CA Certificate | Used to establish trust for intermediate CAs   | [Engine]Validate the Cert chain     | [Vault]pki_int/intermediateCA${YYYYMMDD}                                     |
| Intermediate CA Private Key | Used to sign certificates for intermediate CAs | [Vault]Sign the Server CSR          | [Vault]pki_int/intermediateCA${YYYYMMDD}                                     |
| Server Certificate          | Used to encrypt the license payload            | [Portal]Encrypt the License Payload | [Vault]pki_int/${serialNumber}                                               |
| Server Private Key          | Used to decrypt the license payload            | [Engine]Decrypt the License Payload | [Vault]kv_v1/${serialNumber} <br> [SSM]/app-workflow-v5/license/ServerEncKey |

The Public and Private key uses the EC algorithm with a key size of 256 bits. The certificates are signed using the
SHA-256 hashing algorithm.

## Portal Listing the Server certificate

List all the certificate store on Vault pki_int and display the details of the certificate used for encrypting the
license payload. This can be done using the Vault CLI or API.

## Validity of Certificates

| Certificate Type            | Validity Period |
|-----------------------------|-----------------|
| Root CA Certificate         | 10 years        |
| Intermediate CA Certificate | 5 years         |
| Server Certificate          | 3 year          |

The Server Certificate has a 3-year validity period.

## Handling of Certificate expiration

### Server Certificate Expiration

Even after a Server Certificate expires and a new one is generated in Vault, the system maintains continuity through a
hybrid process:

- The Portal: Continues using the expired certificate to encrypt payloads but applies the new private key for digital
  signatures. It also attaches the new certificate chain to the payload.
- The RTE Engine: Uses the new chain to validate the signature while relying on its embedded private key to decrypt the
  payload.

The Bottom Line: Because the underlying private key does not expire, decryption remains functional during the
certificate transition.

### Intermediate CA Certificate Expiration

The same applies to the Intermediate CA Certificate. When it expires a new one is generated and the new certificate
chain is attached to the payload.

### Root CA Certificate Expiration

However before the Root CA Certificate expires, a new CA will be generated and a new binary will be released with the
new Root CA certificate. This is because the Root CA certificate is used to establish trust and if it expires, the
entire trust chain would be broken. Typically, the Root CA certificate should be renewed at least 2 years before it
expires.

During this time any new Server binary released will contain both the old and new Root CA certificate to ensure that
there is no disruption in service. Once the old CA certificate expires, it can be removed from the binary in subsequent
releases.