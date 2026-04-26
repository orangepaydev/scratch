import { initiateDeveloperControlledWalletsClient } from "@circle-fin/developer-controlled-wallets";
import crypto from "crypto";
import forge from 'node-forge';

// Generate a random 32-byte hex string
// const entitySecret = crypto.randomBytes(32).toString('hex');
// console.log(entitySecret);

// const client = initiateDeveloperControlledWalletsClient({
//   apiKey: "TEST_API_KEY:ea8c31e92799718911a17c7c7f4b2a50:dab79e529a242e79538a55e682758fef",
//   entitySecret: "7efda2738b23a8da4b492c1cb3ec9804ac65853d10624e8bdedd291154d37f32",
// });

// const response = await client.createWalletSet({
//   name: 'My First Wallet Set'
// });

// const walletSetId = response.walletSet.id; 
// console.log("Use this ID:", walletSetId);


// 1. Your 32-byte (64 char) hex string you generated earlier
const entitySecret = "7efda2738b23a8da4b492c1cb3ec9804ac65853d10624e8bdedd291154d37f32"; 
// 2. The Public Key from Circle Console
const publicKeyPem = `-----BEGIN PUBLIC KEY-----
...
-----END PUBLIC KEY-----`;

const publicKey = forge.pki.publicKeyFromPem(publicKeyPem);
const encrypted = publicKey.encrypt(forge.util.hexToBytes(entitySecret), 'RSA-OAEP', {
  md: forge.md.sha256.create(),
  mgf1: {
    md: forge.md.sha256.create()
  }
});

console.log("Your Ciphertext:", forge.util.encode64(encrypted));