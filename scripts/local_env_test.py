import tempfile,unittest
from pathlib import Path
from local_env import load_env
class EnvParsing(unittest.TestCase):
 def parse(self,text):
  with tempfile.TemporaryDirectory() as tmp:
   file=Path(tmp)/'.env';file.write_text(text);return load_env(file,{})
 def test_plain_and_empty(self):self.assertEqual(self.parse('A=value\nB=\n# note'),{'A':'value','B':''})
 def test_quoted_hash_and_spaces(self):self.assertEqual(self.parse("AUTH_PASSWORD='strong # literal $value'\nAUTH_PASSWORD_HASH='$2b$12$literal'"),{'AUTH_PASSWORD':'strong # literal $value','AUTH_PASSWORD_HASH':'$2b$12$literal'})
 def test_export_and_inline_comment(self):self.assertEqual(self.parse('export A="with spaces" # comment\nB=abc#def\nC=plain # comment'),{'A':'with spaces','B':'abc#def','C':'plain'})
 def test_shell_expansion_is_literal(self):self.assertEqual(self.parse('A="$(not-executed)"')['A'],'$(not-executed)')
 def test_error_hides_secret(self):
  with self.assertRaises(ValueError) as error:self.parse("A='sensitive-unclosed")
  self.assertNotIn('sensitive',str(error.exception))
if __name__=='__main__':unittest.main(verbosity=2)
