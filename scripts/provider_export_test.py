"""Additional real HTTP/database checks, run only against the isolated test backend."""
import json,os,time,unittest,urllib.request,uuid
urllib.request.install_opener(urllib.request.build_opener(urllib.request.ProxyHandler({})))
BASE=os.environ['TEST_API_URL'];assert ':18080/' in BASE
def api(path,body=None):
 req=urllib.request.Request(BASE+path,data=None if body is None else json.dumps(body).encode(),headers={'Content-Type':'application/json','Idempotency-Key':str(uuid.uuid4())})
 with urllib.request.urlopen(req,timeout=10) as res:return json.load(res)
class ProvidersAndExport(unittest.TestCase):
 def test_provider_protocols(self):
  settings=api('/settings');self.assertEqual(len(settings['providers']),10)
  for p in settings['providers']:
   with self.subTest(provider=p['id']):
    self.assertTrue(p['configured'])
    source=api('/study-logs',{'studyDate':'2041-01-01','content':'验证跨提供商协议与证据引用 '+p['id'],'durationMinutes':0})
    job=api('/ai/analyses',{'sourceLogId':source['id'],'targetDate':'2041-01-02','budgetMinutes':60,'provider':p['id'],'model':p['defaultModel'],'feedback':''})
    for _ in range(100):
     current=api('/ai/analyses/'+job['id'])
     if current['status'] in ('SUCCEEDED','FAILED'):break
     time.sleep(.1)
    self.assertEqual(current['status'],'SUCCEEDED',current)
    self.assertEqual(current['inputTokens'],100);self.assertEqual(current['outputTokens'],30)
 def test_export_consistency_and_secrets(self):
  with urllib.request.urlopen(BASE+'/exports') as res:
   self.assertIn('attachment',res.headers['Content-Disposition']);self.assertEqual(res.headers['Cache-Control'],'no-store');raw=res.read().decode();export=json.loads(raw)
  self.assertEqual(export['schemaVersion'],'growthhub-export-v3')
  for table,count in export['counts'].items():self.assertEqual(count,len(export['data'][table]))
  self.assertNotIn('test-only-',raw);self.assertNotIn('request_key',raw)
  data=export['data'];tasks={t['id'] for t in data['learning_task']};logs={l['id'] for l in data['study_log']}
  for edge in data['study_log_task']:self.assertIn(edge['task_id'],tasks);self.assertIn(edge['study_log_id'],logs)
if __name__=='__main__':unittest.main(verbosity=2)
