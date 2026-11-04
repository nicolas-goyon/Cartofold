#!/usr/bin/env python3
"""Publish Cartofold to Modrinth: the versions and the page of the project.

    MODRINTH_TOKEN=... python3 scripts/modrinth.py publish --version 1.2.3 --notes notes.md
    MODRINTH_TOKEN=... python3 scripts/modrinth.py project --body docs/modrinth.md --icon logo.png

The project comes from modrinth_project_id in gradle.properties.

`publish` sends the jar as the primary file, and the sources jar and the Javadoc jar as additional files. The Maven of
Modrinth then gives the three files with no token. The files keep the names of Gradle: the Maven finds the suffixes
`-sources` and `-javadoc` in these names. `--type` selects the channel: `release`, or `alpha` for the dev builds. The
Minecraft version and the mod name come from gradle.properties. The jars come from build/libs. A version that Modrinth
already has is not an error, so a failed release can run again. This command needs the scope VERSION_CREATE.

`project` sets the short description (mod_description of gradle.properties), the long description and the icon of the
project. This command needs the scope PROJECT_WRITE.
"""
import argparse
import json
import os
import pathlib
import sys
import urllib.error
import urllib.request
import uuid

API = "https://api.modrinth.com/v2"
USER_AGENT = "nicolas-goyon/cartofold (release workflow)"
SUFFIXES = ("", "-sources", "-javadoc")
TYPES = ("release", "beta", "alpha")
# Cartofold is required on the client and on the server.
ENVIRONMENT = "client_and_server"


def properties(path):
    """The properties of a gradle.properties file."""
    values = {}
    for line in pathlib.Path(path).read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if line and not line.startswith("#") and "=" in line:
            key, value = line.split("=", 1)
            values[key.strip()] = value.strip()
    return values


def metadata(project, version, notes, props, version_type="release"):
    """The JSON of a new version."""
    return {
        "project_id": project,
        "name": f"{props['mod_name']} {version}",
        "version_number": version,
        "changelog": notes,
        "dependencies": [],
        "game_versions": [props["minecraft_version"]],
        "version_type": version_type,
        "loaders": ["neoforge"],
        "environment": ENVIRONMENT,
        "featured": version_type == "release",
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


def exists(token, project, version):
    """True if Modrinth has this version of the project. The token also gives the versions of a draft project."""
    request = urllib.request.Request(f"{API}/project/{project}/version/{version}",
                                     headers={"Authorization": token, "User-Agent": USER_AGENT})
    try:
        with urllib.request.urlopen(request):
            return True
    except urllib.error.HTTPError as error:
        if error.code == 404:
            return False
        raise


def send(request):
    """Sends a request. The result is 0, or 1 if Modrinth refuses the request."""
    try:
        with urllib.request.urlopen(request):
            return 0
    except urllib.error.HTTPError as error:
        print(f"Modrinth refused {request.full_url}: {error.code} {error.read().decode()}", file=sys.stderr)
        return 1


def publish(args, project, token):
    """Sends a new version."""
    props = properties("gradle.properties")
    files = jars(args.libs, props["mod_id"], args.version)
    missing = [str(path) for path in files if not path.is_file()]
    if missing:
        print(f"No file: {', '.join(missing)}", file=sys.stderr)
        return 1
    if exists(token, project, args.version):
        print(f"Modrinth has the version {args.version}: no publication.")
        return 0
    notes = pathlib.Path(args.notes).read_text(encoding="utf-8")
    boundary = uuid.uuid4().hex
    data = metadata(project, args.version, notes, props, args.type)
    request = urllib.request.Request(f"{API}/version", data=multipart(boundary, data, files), method="POST", headers={
        "Authorization": token,
        "Content-Type": f"multipart/form-data; boundary={boundary}",
        "User-Agent": USER_AGENT,
    })
    result = send(request)
    if result == 0:
        print(f"Modrinth has the new version {args.version} ({args.type}).")
    return result


def page(args, project, token):
    """Sets the descriptions and the icon of the project."""
    fields = {
        "description": properties("gradle.properties")["mod_description"],
        "body": pathlib.Path(args.body).read_text(encoding="utf-8"),
    }
    result = send(urllib.request.Request(f"{API}/project/{project}", data=json.dumps(fields).encode(), method="PATCH",
                                         headers={
                                             "Authorization": token,
                                             "Content-Type": "application/json",
                                             "User-Agent": USER_AGENT,
                                         }))
    return result or send(urllib.request.Request(f"{API}/project/{project}/icon?ext=png",
                                                 data=pathlib.Path(args.icon).read_bytes(), method="PATCH", headers={
                                                     "Authorization": token,
                                                     "Content-Type": "image/png",
                                                     "User-Agent": USER_AGENT,
                                                 }))


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    commands = parser.add_subparsers(dest="command", required=True)
    version = commands.add_parser("publish", help="send a new version")
    version.add_argument("--version", required=True, help="the version")
    version.add_argument("--notes", required=True, help="the file of the notes of the version")
    version.add_argument("--type", choices=TYPES, default="release", help="the channel of the version")
    version.add_argument("--libs", default="build/libs", help="the folder of the jars")
    version.set_defaults(run=publish)
    project_page = commands.add_parser("project", help="set the page of the project")
    project_page.add_argument("--body", required=True, help="the file of the long description")
    project_page.add_argument("--icon", required=True, help="the PNG file of the icon")
    project_page.set_defaults(run=page)
    args = parser.parse_args()
    return args.run(args, properties("gradle.properties")["modrinth_project_id"], os.environ["MODRINTH_TOKEN"])


if __name__ == "__main__":
    sys.exit(main())
