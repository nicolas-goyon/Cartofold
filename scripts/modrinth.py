#!/usr/bin/env python3
"""Publish a version of Cartofold to Modrinth.

The script sends the jar as the primary file, and the sources jar and the Javadoc jar as additional files. The Maven of
Modrinth then gives the three files with no token. The files keep the names of Gradle: the Maven finds the suffixes
`-sources` and `-javadoc` in these names.

    MODRINTH_PROJECT_ID=... MODRINTH_TOKEN=... python3 scripts/modrinth.py --version 1.2.3 --notes notes.md

The Minecraft version and the mod name come from gradle.properties. The jars come from build/libs. A version that
Modrinth already has is not an error, so a failed release can run again.
"""
import argparse
import json
import os
import pathlib
import sys
import urllib.error
import urllib.request
import uuid

API = "https://api.modrinth.com/v2/version"
USER_AGENT = "nicolas-goyon/cartofold (release workflow)"
SUFFIXES = ("", "-sources", "-javadoc")


def properties(path):
    """The properties of a gradle.properties file."""
    values = {}
    for line in pathlib.Path(path).read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if line and not line.startswith("#") and "=" in line:
            key, value = line.split("=", 1)
            values[key.strip()] = value.strip()
    return values


def metadata(project, version, notes, props):
    """The JSON of a new version."""
    return {
        "project_id": project,
        "name": f"{props['mod_name']} {version}",
        "version_number": version,
        "changelog": notes,
        "dependencies": [],
        "game_versions": [props["minecraft_version"]],
        "version_type": "release",
        "loaders": ["neoforge"],
        "featured": True,
        "status": "listed",
        "file_parts": [f"file{index}" for index in range(len(SUFFIXES))],
        "primary_file": "file0",
    }


def jars(libs, mod_id, version):
    """The paths of the jars, the primary jar first."""
    return [pathlib.Path(libs) / f"{mod_id}-{version}{suffix}.jar" for suffix in SUFFIXES]


def multipart(boundary, data, files):
    """The body of a multipart request."""
    body = bytearray()
    prefix = f"--{boundary}\r\n"
    body += prefix.encode()
    body += b'Content-Disposition: form-data; name="data"\r\nContent-Type: application/json\r\n\r\n'
    body += json.dumps(data).encode() + b"\r\n"
    for index, path in enumerate(files):
        body += prefix.encode()
        body += (f'Content-Disposition: form-data; name="file{index}"; filename="{path.name}"\r\n'
                 "Content-Type: application/java-archive\r\n\r\n").encode()
        body += path.read_bytes() + b"\r\n"
    body += f"--{boundary}--\r\n".encode()
    return bytes(body)


def exists(project, version):
    """True if Modrinth has this version of the project."""
    request = urllib.request.Request(f"https://api.modrinth.com/v2/project/{project}/version/{version}",
                                     headers={"User-Agent": USER_AGENT})
    try:
        with urllib.request.urlopen(request):
            return True
    except urllib.error.HTTPError as error:
        if error.code == 404:
            return False
        raise


def publish(token, data, files):
    """Sends a new version."""
    boundary = uuid.uuid4().hex
    request = urllib.request.Request(API, data=multipart(boundary, data, files), method="POST", headers={
        "Authorization": token,
        "Content-Type": f"multipart/form-data; boundary={boundary}",
        "User-Agent": USER_AGENT,
    })
    with urllib.request.urlopen(request):
        pass


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--version", required=True, help="the version of the release")
    parser.add_argument("--notes", required=True, help="the file of the release notes")
    parser.add_argument("--libs", default="build/libs", help="the folder of the jars")
    args = parser.parse_args()
    project = os.environ["MODRINTH_PROJECT_ID"]
    props = properties("gradle.properties")
    files = jars(args.libs, props["mod_id"], args.version)
    missing = [str(path) for path in files if not path.is_file()]
    if missing:
        print(f"No file: {', '.join(missing)}", file=sys.stderr)
        return 1
    if exists(project, args.version):
        print(f"Modrinth has the version {args.version}: no publication.")
        return 0
    notes = pathlib.Path(args.notes).read_text(encoding="utf-8")
    try:
        publish(os.environ["MODRINTH_TOKEN"], metadata(project, args.version, notes, props), files)
    except urllib.error.HTTPError as error:
        print(f"Modrinth refused the version: {error.code} {error.read().decode()}", file=sys.stderr)
        return 1
    print(f"Modrinth has the new version {args.version}.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
