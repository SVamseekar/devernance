"""Offline verification of an evidence bundle. Standard library only, so an auditor can read every line.

A bundle directory holds:
    manifest.json    lists every file with its SHA-256
    signature.jws    Devernance's signature (JWS, RS256) over the manifest's SHA-256
    jwks.json        the public keys as they were when the bundle was exported (convenience copy)
    ...              the evidence files named in the manifest

Verification answers two questions:
    1. Were these exact files issued by the holder of the signing key?   (signature + hashes)
    2. Is anything in the folder that the manifest does not vouch for?    (extra files)

It cannot answer whether every real-world action was recorded. The manifest's `limitation` says so.
For an independent check, take the public keys from the issuer's own URL (--jwks https://.../.well-known/jwks.json),
not from the copy inside the bundle.
"""

import base64
import hashlib
import hmac
import json
import os

# DER prefix of a SHA-256 DigestInfo (RFC 8017, section 9.2).
_SHA256_DIGEST_INFO = bytes.fromhex("3031300d060960864801650304020105000420")
_RESERVED = ("manifest.json", "signature.jws", "jwks.json")


class VerificationError(Exception):
    """The bundle is not what it claims to be. The message says what failed."""


def _b64url(text):
    return base64.urlsafe_b64decode(text + "=" * (-len(text) % 4))


def rs256_verify(signing_input, signature, modulus, exponent):
    """RSASSA-PKCS1-v1_5 with SHA-256 (RFC 8017, section 8.2.2). True when the signature is valid."""
    length = (modulus.bit_length() + 7) // 8
    if length < 256 or len(signature) != length:
        return False
    number = int.from_bytes(signature, "big")
    if number >= modulus:
        return False
    recovered = pow(number, exponent, modulus).to_bytes(length, "big")
    digest_info = _SHA256_DIGEST_INFO + hashlib.sha256(signing_input).digest()
    expected = b"\x00\x01" + b"\xff" * (length - len(digest_info) - 3) + b"\x00" + digest_info
    return hmac.compare_digest(recovered, expected)


def verify_jws(token, jwks):
    """Verifies a compact JWS against a JWKS (dict). Returns the payload (dict)."""
    parts = token.strip().split(".")
    if len(parts) != 3:
        raise VerificationError("The signature is not a compact JWS")
    try:
        header = json.loads(_b64url(parts[0]))
        payload = json.loads(_b64url(parts[1]))
        signature = _b64url(parts[2])
    except (ValueError, TypeError):
        raise VerificationError("The signature cannot be decoded") from None
    if header.get("alg") != "RS256":
        raise VerificationError("Unexpected signature algorithm: %r" % header.get("alg"))
    key = None
    for candidate in (jwks or {}).get("keys", []):
        if candidate.get("kid") == header.get("kid") and candidate.get("kty") == "RSA":
            key = candidate
            break
    if key is None:
        raise VerificationError("No public key with id %r in the key set" % header.get("kid"))
    modulus = int.from_bytes(_b64url(key["n"]), "big")
    exponent = int.from_bytes(_b64url(key["e"]), "big")
    signing_input = (parts[0] + "." + parts[1]).encode("ascii")
    if not rs256_verify(signing_input, signature, modulus, exponent):
        raise VerificationError("The signature does not match the public key")
    return payload


def sha256_hex(data):
    return hashlib.sha256(data).hexdigest()


def safe_join(root, relative):
    """`root/relative`, refusing absolute paths and anything that climbs out of `root`."""
    if not relative or os.path.isabs(relative) or "\\" in relative:
        raise VerificationError("Unsafe file path in bundle: %r" % relative)
    parts = relative.split("/")
    if any(part in ("", ".", "..") for part in parts):
        raise VerificationError("Unsafe file path in bundle: %r" % relative)
    return os.path.join(root, *parts)


def write_bundle(bundle, directory, jwks=None):
    """Writes the API's evidence-bundle answer to `directory` exactly as received. Returns the file count."""
    os.makedirs(directory, exist_ok=True)
    files = bundle.get("files") or {}
    for path, content in files.items():
        if path in _RESERVED:
            raise VerificationError("Bundle file uses a reserved name: %r" % path)
        target = safe_join(directory, path)
        os.makedirs(os.path.dirname(target), exist_ok=True)
        with open(target, "wb") as handle:
            handle.write(content.encode("utf-8"))
    with open(os.path.join(directory, "manifest.json"), "wb") as handle:
        handle.write(bundle["manifest"].encode("utf-8"))
    with open(os.path.join(directory, "signature.jws"), "w", encoding="ascii") as handle:
        handle.write(bundle["signature"])
    if jwks is not None:
        with open(os.path.join(directory, "jwks.json"), "w", encoding="utf-8") as handle:
            json.dump(jwks, handle, indent=2)
    return len(files)


def verify_bundle(directory, jwks=None):
    """Verifies the bundle in `directory`. Returns a summary dict; raises VerificationError on any failure.

    jwks: the issuer's public keys (dict). When None, the bundle's own jwks.json is used and the summary
    says `independentKeys: False`.
    """
    manifest_path = os.path.join(directory, "manifest.json")
    signature_path = os.path.join(directory, "signature.jws")
    for required in (manifest_path, signature_path):
        if not os.path.isfile(required):
            raise VerificationError("Missing %s" % os.path.basename(required))
    with open(manifest_path, "rb") as handle:
        manifest_bytes = handle.read()
    with open(signature_path, "r", encoding="ascii") as handle:
        token = handle.read()

    independent = jwks is not None
    if jwks is None:
        bundled = os.path.join(directory, "jwks.json")
        if not os.path.isfile(bundled):
            raise VerificationError("No public keys: pass --jwks or keep jwks.json in the bundle")
        with open(bundled, "r", encoding="utf-8") as handle:
            jwks = json.load(handle)

    claims = verify_jws(token, jwks)
    if claims.get("purpose") != "governance-evidence-bundle":
        raise VerificationError("The signature is not for an evidence bundle")
    manifest_sha = sha256_hex(manifest_bytes)
    if claims.get("manifestSha256") != manifest_sha:
        raise VerificationError("manifest.json was changed after it was signed")
    try:
        manifest = json.loads(manifest_bytes.decode("utf-8"))
    except ValueError:
        raise VerificationError("manifest.json is not valid JSON") from None

    listed = set()
    for entry in manifest.get("files", []):
        path = entry["path"]
        listed.add(path)
        target = safe_join(directory, path)
        if not os.path.isfile(target):
            raise VerificationError("Missing file: %s" % path)
        with open(target, "rb") as handle:
            if sha256_hex(handle.read()) != entry["sha256"]:
                raise VerificationError("File was changed after export: %s" % path)

    extra = []
    for folder, _, names in os.walk(directory):
        for name in names:
            relative = os.path.relpath(os.path.join(folder, name), directory).replace(os.sep, "/")
            if relative not in listed and relative not in _RESERVED:
                extra.append(relative)
    if extra:
        raise VerificationError("Files not covered by the manifest: %s" % ", ".join(sorted(extra)))

    return {
        "valid": True,
        "system": (manifest.get("system") or {}).get("name"),
        "systemId": claims.get("systemId"),
        "generatedAt": manifest.get("generatedAt"),
        "period": manifest.get("period"),
        "counts": manifest.get("counts"),
        "files": len(listed),
        "manifestSha256": manifest_sha,
        "auditChainHead": manifest.get("auditChainHead"),
        "independentKeys": independent,
        "limitation": manifest.get("limitation"),
    }
