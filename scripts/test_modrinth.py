"""Tests of the publication to Modrinth: python3 -m unittest discover -s scripts -p 'test_*.py'."""
import json
import pathlib
import tempfile
import unittest

import modrinth


class ModrinthTest(unittest.TestCase):
    def test_metadata_has_all_the_files_and_the_primary_jar(self):
        props = {"mod_name": "Cartofold", "minecraft_version": "1.21.1"}

        data = modrinth.metadata("abc", "1.2.3", "Notes", props)

        self.assertEqual(["file0", "file1", "file2"], data["file_parts"])
        self.assertEqual("file0", data["primary_file"])
        self.assertEqual(["1.21.1"], data["game_versions"])
        self.assertEqual(["neoforge"], data["loaders"])
        self.assertEqual("client_and_server", data["environment"])
        self.assertEqual("Cartofold 1.2.3", data["name"])

    def test_dev_build_is_an_alpha_that_is_not_featured(self):
        props = {"mod_name": "Cartofold", "minecraft_version": "1.21.1"}

        data = modrinth.metadata("abc", "1.2.3-dev.abc1234", "Notes", props, "alpha")

        self.assertEqual("alpha", data["version_type"])
        self.assertFalse(data["featured"])

    def test_jars_keep_the_suffixes_of_the_maven(self):
        names = [path.name for path in modrinth.jars("build/libs", "cartofold", "1.2.3")]

        self.assertEqual(["cartofold-1.2.3.jar", "cartofold-1.2.3-sources.jar", "cartofold-1.2.3-javadoc.jar"], names)

    def test_multipart_has_the_data_and_each_file(self):
        with tempfile.TemporaryDirectory() as folder:
            path = pathlib.Path(folder) / "a.jar"
            path.write_bytes(b"JAR")

            body = modrinth.multipart("edge", {"name": "x"}, [path])

        self.assertIn(json.dumps({"name": "x"}).encode(), body)
        self.assertIn(b'name="file0"; filename="a.jar"', body)
        self.assertIn(b"\r\n\r\nJAR\r\n", body)
        self.assertTrue(body.endswith(b"--edge--\r\n"))

    def test_properties_skip_the_comments(self):
        with tempfile.TemporaryDirectory() as folder:
            path = pathlib.Path(folder) / "gradle.properties"
            path.write_text("# a comment\nmod_id=cartofold\n\nkey = a=b\n", encoding="utf-8")

            values = modrinth.properties(path)

        self.assertEqual({"mod_id": "cartofold", "key": "a=b"}, values)


if __name__ == "__main__":
    unittest.main()
