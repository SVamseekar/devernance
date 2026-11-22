"""Where the CLI and SDK find the server address and API key.

Order: explicit argument, then environment (DEVERNANCE_SERVER, DEVERNANCE_API_KEY), then the file written by
`devernance login` (~/.config/devernance/config.json, readable only by its owner).
"""

import json
import os

ENV_SERVER = "DEVERNANCE_SERVER"
ENV_API_KEY = "DEVERNANCE_API_KEY"
ENV_CONFIG = "DEVERNANCE_CONFIG"


def config_path():
    override = os.environ.get(ENV_CONFIG)
    if override:
        return override
    base = os.environ.get("XDG_CONFIG_HOME") or os.path.join(os.path.expanduser("~"), ".config")
    return os.path.join(base, "devernance", "config.json")


def load_file():
    try:
        with open(config_path(), "r", encoding="utf-8") as handle:
            data = json.load(handle)
            return data if isinstance(data, dict) else {}
    except (OSError, ValueError):
        return {}


def save_file(server, api_key):
    path = config_path()
    os.makedirs(os.path.dirname(path), mode=0o700, exist_ok=True)
    # Created with owner-only permissions from the start, so the key is never world-readable.
    descriptor = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
    with os.fdopen(descriptor, "w", encoding="utf-8") as handle:
        json.dump({"server": server, "apiKey": api_key}, handle, indent=2)
        handle.write("\n")
    os.chmod(path, 0o600)
    return path


def resolve(server=None, api_key=None):
    """Returns (server, api_key); either may be None when it is configured nowhere."""
    stored = load_file()
    server = server or os.environ.get(ENV_SERVER) or stored.get("server")
    api_key = api_key or os.environ.get(ENV_API_KEY) or stored.get("apiKey")
    return (server.rstrip("/") if server else None), api_key
