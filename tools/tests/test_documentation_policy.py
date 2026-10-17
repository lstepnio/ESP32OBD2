"""Regression cases for documentation mistakes that mislead a new coding session."""
import json
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from documentation_policy import check_links, check_map, heading_anchors


class DocumentationPolicyTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.save('docs/backlog.md', '# Backlog\n\n| QUAL-01 | Ready | Test |\n')
        self.save('docs/roadmap.md', '# Roadmap\n\nNext: **QUAL-01**.\n')
        self.save('src/owner.kt', 'source')
        self.documents = [self.root / 'docs/backlog.md', self.root / 'docs/roadmap.md']
        self.manifest = dict(schemaVersion=1,
                             entrypoints=dict(backlog='docs/backlog.md', roadmap='docs/roadmap.md'),
                             documents=dict(current=['docs/backlog.md', 'docs/roadmap.md']),
                             sourceAreas=dict(owner=['src/owner.kt']))
        self.save_map()

    def save(self, path, text):
        target = self.root / path
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(text)
        return target

    def save_map(self):
        self.save('docs/documentation-map.json', json.dumps(self.manifest))

    def test_unicode_repeated_headings_and_code_examples(self):
        anchors = heading_anchors('# Café: owner & IO\n## Recovery\n## Recovery\n```md\n# Imaginary\n```\n')
        self.assertEqual({'café-owner--io', 'recovery', 'recovery-1'}, anchors)

    def test_local_unicode_anchor_and_encoded_space_destination(self):
        self.save('docs/Other guide.md', '# Café\n')
        document = self.save('docs/index.md', '[guide](Other%20guide.md#caf%C3%A9)\n# Here\n[self](#here)\n')
        check_links(self.root, [document])

    def test_missing_local_file_fails(self):
        document = self.save('docs/index.md', '[guide](absent.md)\n')
        with self.assertRaisesRegex(ValueError, 'Broken documentation link'):
            check_links(self.root, [document])

    def test_heading_removed_from_existing_file_fails(self):
        document = self.save('docs/index.md', '[guide](backlog.md#obsolete-heading)\n')
        with self.assertRaisesRegex(ValueError, 'Broken documentation heading'):
            check_links(self.root, [document])

    def test_examples_and_external_links_do_not_require_local_files(self):
        document = self.save('docs/index.md', '[web](https://example.invalid/a#b)\n```md\n[example](missing.md)\n```\n')
        check_links(self.root, [document])

    def test_valid_map(self):
        check_map(self.root, self.documents)

    def test_new_unclassified_document_fails(self):
        extra = self.save('docs/unowned.md', '# New\n')
        with self.assertRaisesRegex(ValueError, 'coverage mismatch'):
            check_map(self.root, self.documents + [extra])

    def test_duplicate_ownership_fails(self):
        self.manifest['documents']['other'] = ['docs/backlog.md']
        self.save_map()
        with self.assertRaisesRegex(ValueError, 'more than once'):
            check_map(self.root, self.documents)

    def test_stale_source_context_fails(self):
        self.manifest['sourceAreas']['owner'] = ['src/renamed.kt']
        self.save_map()
        with self.assertRaisesRegex(ValueError, 'Stale AI context path'):
            check_map(self.root, self.documents)

    def test_roadmap_task_without_backlog_gate_fails(self):
        self.save('docs/roadmap.md', '# Roadmap\nNext QUAL-99\n')
        with self.assertRaisesRegex(ValueError, 'undefined backlog IDs'):
            check_map(self.root, self.documents)

    def test_duplicate_backlog_identity_fails(self):
        self.save('docs/backlog.md', '| QUAL-01 | Ready |\n| QUAL-01 | Blocked |\n')
        with self.assertRaisesRegex(ValueError, 'duplicate backlog'):
            check_map(self.root, self.documents)

    def test_historical_report_requires_scope(self):
        extra = self.save('docs/evidence/old.md', '# Earlier rollout\nInstall old image\n')
        with self.assertRaisesRegex(ValueError, 'Missing historical scope'):
            check_map(self.root, self.documents + [extra])
        extra.write_text('# Earlier rollout\n> Historical evidence.\n> [state](../current-state.md) [tasks](../backlog.md)\n')
        check_map(self.root, self.documents + [extra])


if __name__ == '__main__':
    unittest.main()
