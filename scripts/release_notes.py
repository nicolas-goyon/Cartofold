#!/usr/bin/env python3
"""Write the page of the release notes of the documentation from the GitHub Releases.

    gh api --paginate repos/OWNER/NAME/releases > releases.json
    python3 scripts/release_notes.py releases.json docs/release-notes.md
"""
import argparse
import json
import pathlib
import re

HEADER = """# Release notes

The versions of Cartofold, newest first. Each version is also on
[Modrinth](https://modrinth.com/project/cartofold) and in the
[GitHub Releases](https://github.com/nicolas-goyon/Cartofold/releases).
"""


def section(release):
    """The section of a release. The headings of the notes go one level down."""
    date = release["published_at"][:10]
    body = re.sub(r"^(#+) ", r"##\1 ", (release["body"] or "").strip(), flags=re.MULTILINE)
    return f"## {release['name'] or release['tag_name']}\n\nReleased on {date}. [GitHub Release]({release['html_url']})\n\n{body}\n"


def page(releases):
    """The page of the published releases, in the order of the list."""
    published = [release for release in releases if not release["draft"] and not release["prerelease"]]
    if not published:
        return HEADER + "\nNo release yet.\n"
    return HEADER + "\n" + "\n".join(section(release) for release in published)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("releases", help="the JSON file of the GitHub API")
    parser.add_argument("output", help="the Markdown file")
    args = parser.parse_args()
    text = page(json.loads(pathlib.Path(args.releases).read_text(encoding="utf-8")))
    pathlib.Path(args.output).write_text(text, encoding="utf-8")


if __name__ == "__main__":
    main()
