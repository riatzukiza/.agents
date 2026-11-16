import importlib.util,sys,unittest,os,tempfile
from unittest.mock import patch
from pathlib import Path
p=Path(__file__).resolve().parent/'promethean_rest_dns.py'
spec=importlib.util.spec_from_file_location('dns_helper',p);m=importlib.util.module_from_spec(spec);sys.modules[spec.name]=m;spec.loader.exec_module(m)
class DNSHelperTests(unittest.TestCase):
 def test_knoxx_cli(self):
  a=m.build_parser().parse_args(['ensure','testing.knoxx','--core','knoxx','--dry-run'])
  self.assertEqual(a.core,['knoxx']);self.assertTrue(a.dry_run);self.assertFalse(a.proxied)
 def test_labels(self):
  for s in ['testing.knoxx','stealth.knoxx','yoga.knoxx','staging.knoxx','*.knoxx']:
   self.assertEqual(m.normalize_host_label(s+'.promethean.rest.','promethean.rest'),s)
 def test_reject_bad_labels(self):
  for s in ['promethean.rest','','testing..knoxx','https://testing.knoxx','a.*.knoxx','-bad.knoxx','a'*64+'.knoxx']:
   with self.assertRaises(SystemExit):m.normalize_host_label(s,'promethean.rest')
 def test_nonrouting_records_preserved_and_idempotent(self):
  a=m.DNSRecord('testing.knoxx.promethean.rest','A','157.245.125.134',record_id='a')
  t=m.DNSRecord(a.name,'TXT','non-secret-fixture',record_id='t')
  self.assertEqual([x['action'] for x in m.plan_record_changes([a,t],[a])],['keep','preserve'])
 def test_conflict_replacement(self):
  a=m.DNSRecord('testing.knoxx.promethean.rest','A','157.245.125.134')
  c=m.DNSRecord(a.name,'CNAME','example.com',record_id='c')
  self.assertEqual([x['action'] for x in m.plan_record_changes([c],[a])],['delete','create'])
 def test_ip_validation(self):
  for ip in ['::1','garbage','256.1.1.1']:
   with self.assertRaises(SystemExit):m.build_desired_records('testing.knoxx.promethean.rest',[],[ip],1,False)
 def test_literal_env_file(self):
  with tempfile.TemporaryDirectory() as directory:
   p=Path(directory)/'settings.env'
   p.write_text("export CLOUDFLARE_API_TOKEN='test-only-placeholder' # comment\nIGNORED_SECRET=not-loaded\nCLOUDFLARE_ZONE_NAME=promethean.rest\n")
   with patch.dict(os.environ,{},clear=True):
    m.load_env_file(str(p));self.assertEqual(os.environ['CLOUDFLARE_API_TOKEN'],'test-only-placeholder');self.assertNotIn('IGNORED_SECRET',os.environ)
    os.environ['CLOUDFLARE_API_TOKEN']='environment-wins';m.load_env_file(str(p));self.assertEqual(os.environ['CLOUDFLARE_API_TOKEN'],'environment-wins')
 def test_bad_env_value_does_not_echo_secret(self):
  with tempfile.TemporaryDirectory() as directory:
   p=Path(directory)/'settings.env';p.write_text("CLOUDFLARE_API_TOKEN='unclosed-test-only-placeholder")
   with patch.dict(os.environ,{},clear=True):
    with self.assertRaises(SystemExit) as caught:m.load_env_file(str(p))
    self.assertNotIn('unclosed-test-only-placeholder',str(caught.exception))

unittest.main()
