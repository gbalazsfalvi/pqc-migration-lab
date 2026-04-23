# DSSE profile

## Signed representation

The encoder implements DSSE v1 pre-authentication encoding:

```text
PAE(type, body) =
    "DSSEv1" SP decimal_length(type_utf8) SP type_utf8
             SP decimal_length(body) SP body
```

Lengths count bytes. Decimal lengths use ASCII without leading zeroes. The type is case-sensitive UTF-8 and must contain well-formed Unicode. The body is an opaque byte sequence.

Independent literal examples used by the tests:

```text
type=""   body=""             -> "DSSEv1 0  0 "
type="t"  body="hello world"  -> "DSSEv1 1 t 11 hello world"
type="é"  body="x"            -> UTF8("DSSEv1 2 é 1 x")
```

There is no extra terminator after the body. Binary payload bytes, embedded zeroes and whitespace are preserved.

## Wire envelope

```json
{
  "payloadType": "application/vnd.pqclab.release-manifest.v1+json",
  "payload": "<base64 of the original payload bytes>",
  "signatures": [
    {
      "keyid": "<optional unauthenticated lookup hint>",
      "sig": "<base64 of a signature over PAE>"
    }
  ]
}
```

The JSON representation is not signed. Both standard and URL-safe base64 are accepted, including supported padded and unpadded forms. A changed encoding of the same bytes does not invalidate a signature. Whitespace inside a base64 value and mixed alphabets are rejected.

Unknown envelope and signature fields are ignored for DSSE extension compatibility. Duplicate keys, multiple top-level JSON documents, invalid UTF-8, null required values and implicit string coercion are rejected.

An empty signature list can be represented and parsed, but cannot produce a verified result. Signing requires at least one explicit signer.

## Laboratory resource limits

These are application profile limits, not universal DSSE protocol limits.

| Boundary | Maximum |
| --- | ---: |
| Complete UTF-8 JSON document | 1,048,576 bytes |
| JSON nesting depth | 16 |
| JSON number token | 256 characters |
| Payload | 65,536 decoded bytes |
| Payload type | 1,024 UTF-8 bytes |
| Signatures | 4 |
| Individual signature | 16,384 decoded bytes |
| Key hint | 256 Java string characters |

Bounds are checked before cryptographic verification. The codec also rejects oversized encoded values before base64 decoding. Parser diagnostics exposed to callers do not retain input fragments or the underlying parser exception.

## Verification contract

`DsseVerifier` receives:

1. A parsed, immutable envelope.
2. The payload type accepted by the receiving application.
3. A verification port bound to an explicitly trusted public key.

It decodes the payload once for that verification, frames that snapshot, and returns a defensive copy of the same bytes on success. Calling code must consume this verified result rather than parsing the original envelope again.

The verifier finds a valid signature for that one key. It ignores `keyid` because the hint is not authenticated. A sender cannot turn an arbitrary embedded or advertised key into a trusted identity.

For ML-DSA-65, the JCA adapter uses the pure signature algorithm with its default empty context. DSSE's PAE frame supplies protocol-level payload-type binding. HashML-DSA, custom ML-DSA contexts and alternative parameter sets are outside this milestone.

An invalid signature returns `false`. Provider setup, unsupported algorithms and invalid key configuration raise `CryptoOperationException`; those conditions must not masquerade as normal signature rejection.

## Two signatures and migration policy

A DSSE envelope can contain an Ed25519 signature and an ML-DSA-65 signature over the same PAE bytes. The technical probe verifies both independently.

Removing the ML-DSA entry leaves an independently valid Ed25519 signature. The application layer now explicitly rejects that stripped envelope under `BOTH_REQUIRED`; the protocol primitive continues to verify one supplied key.

Milestone two obtains algorithm, key ownership and required signature sets from trusted receiver configuration. Neither entry count nor unauthenticated hints provide that policy. See [the integration contract](milestone-02.md).

## Sources

- [DSSE protocol](https://github.com/dsse-lf/dsse/blob/master/protocol.md)
- [DSSE JSON envelope](https://github.com/dsse-lf/dsse/blob/master/envelope.md)
- [FIPS 204](https://csrc.nist.gov/pubs/fips/204/final)
