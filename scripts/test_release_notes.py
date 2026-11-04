"""Tests of the page of the release notes."""
import unittest

import release_notes


def release(tag, **fields):
    return {"tag_name": tag, "name": f"Cartofold {tag}", "body": "## Versions\n\nNotes", "draft": False,
            "prerelease": False, "published_at": "2026-11-04T07:30:00Z", "html_url": f"https://example.org/{tag}",
            **fields}


class ReleaseNotesTest(unittest.TestCase):
    def test_section_has_the_name_and_the_date_and_moves_the_headings_down(self):
        text = release_notes.section(release("1.2.3"))

        self.assertIn("## Cartofold 1.2.3", text)
        self.assertIn("Released on 2026-11-04.", text)
        self.assertIn("#### Versions", text)

    def test_page_keeps_only_the_published_releases(self):
        text = release_notes.page([release("1.0.0"), release("0.9.0", draft=True), release("0.8.0", prerelease=True)])

        self.assertIn("Cartofold 1.0.0", text)
        self.assertNotIn("0.9.0", text)
        self.assertNotIn("0.8.0", text)

    def test_page_with_no_release(self):
        self.assertIn("No release yet.", release_notes.page([]))

    def test_release_with_no_body(self):
        self.assertIn("## Cartofold 1.0.0", release_notes.section(release("1.0.0", body=None)))


if __name__ == "__main__":
    unittest.main()
