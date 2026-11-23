#!/usr/bin/env python3
"""
OptiCareer Platform - Security Secret Initialization Utility
Generates cryptographically secure 256-bit keys and initializes .env with private permissions (0600).
"""
import base64
import os
from pathlib import Path
import secrets

def main():
    root_dir = Path(__file__).resolve().parent.parent
    template_path = root_dir / ".env.example"
    env_path = root_dir / ".env"

    if not template_path.exists():
        print(f"Error: Template not found at {template_path}")
        return 1

    if env_path.exists():
        print(f"Notice: {env_path} already exists. Skipping initialization to avoid overwriting existing secrets.")
        return 0

    template = template_path.read_text(encoding="utf-8")
    generated = {
        "DATABASE_PASSWORD": secrets.token_urlsafe(24),
        "REDIS_PASSWORD": secrets.token_urlsafe(24),
        "JWT_KEY": base64.b64encode(secrets.token_bytes(32)).decode(),
        "ENCRYPTION_KEY": base64.b64encode(secrets.token_bytes(32)).decode(),
    }

    lines = []
    for line in template.splitlines():
        if "=" in line and not line.strip().startswith("#"):
            key, _ = line.split("=", 1)
            key = key.strip()
            if key in generated:
                lines.append(f"{key}={generated[key]}")
                continue
        lines.append(line)

    flags = os.O_WRONLY | os.O_CREAT | os.O_EXCL
    mode = 0o600  # Read/Write by owner only (fintech security standard)
    descriptor = os.open(str(env_path), flags, mode)
    with os.fdopen(descriptor, "w", encoding="utf-8") as output:
        output.write("\n".join(lines) + "\n")

    print("Successfully created .env with private (0600) permissions.")
    print("Generated 256-bit cryptographically secure keys for JWT_KEY and ENCRYPTION_KEY.")
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
