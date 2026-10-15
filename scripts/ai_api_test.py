"""AI coach integration tests, using real PostgreSQL and local HTTP provider fixtures."""
import concurrent.futures,datetime,json,os,subprocess,time,unittest,urllib.error,urllib.request,uuid
urllib.request.install_opener(urllib.request.build_opener(urllib.request.ProxyHandler({})))
BASE=os.environ.get('TEST_API_URL','http://127.0.0.1:18080/api/v1')
if ':18080/' not in BASE:raise RuntimeError('Use only the isolated test server')
def call(path,method='GET',body=None,key=None):
 headers={'Content-Type':'application/json'}
 if key:headers['Idempotency-Key']=key
 req=urllib.request.Request(BASE+path,method=method,headers=headers,data=None if body is None else json.dumps(body,ensure_ascii=False).encode())
 try:
  with urllib.request.urlopen(req,timeout=15) as res:return res.status,json.load(res)
 except urllib.error.HTTPError as e:
  try:return e.code,json.load(e)
  finally:e.close()
class AiFlow(unittest.TestCase):
 counter=0
 @classmethod
 def setUpClass(cls):
  code,settings=call('/settings');assert code==200
  call('/settings','PATCH',{'version':settings['version'],'provider':'OPENAI','model':'test-openai','dailyMinutes':60,'preferences':'优先实践与测试'})
 def request(self,mode='valid',provider='OPENAI',budget=60,task_ids=None):
  AiFlow.counter+=1
  date=(datetime.date(2038,1,1)+datetime.timedelta(days=AiFlow.counter)).isoformat()
  code,log=call('/study-logs','POST',{'studyDate':'2035-01-01','content':'M2 实践记录 '+str(uuid.uuid4()),'durationMinutes':30,'taskIds':task_ids or []})
  self.assertEqual(code,201)
  return {'sourceLogId':log['id'],'targetDate':date,'budgetMinutes':budget,'provider':provider,'model':'test-openai' if provider=='OPENAI' else 'test-deepseek','feedback':'[test:'+mode+']'},log
 def submit(self,request,key=None):
  code,a=call('/ai/analyses','POST',request,key or str(uuid.uuid4()));self.assertEqual(code,202,a);return a
 def finish(self,a):
  deadline=time.monotonic()+15
  while time.monotonic()<deadline:
   code,a=call('/ai/analyses/'+a['id']);self.assertEqual(code,200,a)
   if a['status'] in ['SUCCEEDED','FAILED']:return a
   time.sleep(.1)
  self.fail('analysis did not finish')
 def success(self,mode='valid',**kwargs):
  request,log=self.request(mode,**kwargs);a=self.finish(self.submit(request));self.assertEqual(a['status'],'SUCCEEDED',a)
  code,s=call('/plan-suggestions/'+a['suggestionId']);self.assertEqual(code,200);return request,log,a,s
 def test_01_openai_contract_and_snapshot(self):
  req,log,a,s=self.success();self.assertEqual(a['attemptCount'],1);self.assertEqual(a['inputTokens'],100)
  self.assertEqual(a['snapshot']['source']['content'],log['content']);self.assertEqual(s['status'],'DRAFT')
  self.assertEqual(call('/tasks?date='+req['targetDate'])[1]['total'],0)
  serialized=json.dumps(a);self.assertNotIn('test-only-openai',serialized);self.assertNotIn('Authorization',serialized)
 def test_02_deepseek_contract(self):
  _,_,a,_=self.success(provider='DEEPSEEK');self.assertEqual(a['provider'],'DEEPSEEK')
 def test_03_same_request_key_is_idempotent(self):
  request,_=self.request('slow');key=str(uuid.uuid4())
  with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:results=list(pool.map(lambda _:call('/ai/analyses','POST',request,key),range(2)))
  self.assertEqual([c for c,a in results],[202,202]);self.assertEqual(results[0][1]['id'],results[1][1]['id'])
  changed={**request,'budgetMinutes':55};self.assertEqual(call('/ai/analyses','POST',changed,key)[0],409)
  self.assertEqual(self.finish(results[0][1])['status'],'SUCCEEDED')
 def test_04_only_one_active_analysis(self):
  request,_=self.request('slow');a=self.submit(request)
  other={**request,'feedback':'other request'};code,error=call('/ai/analyses','POST',other,str(uuid.uuid4()));self.assertEqual(code,409);self.assertEqual(error['code'],'ANALYSIS_BUSY');self.finish(a)
 def test_05_invalid_json_is_repaired_once(self):
  _,_,a,_=self.success('invalid-once');self.assertEqual(a['attemptCount'],2);self.assertEqual(a['inputTokens'],200)
 def test_06_invalid_json_never_creates_tasks(self):
  request,_=self.request('always-invalid');a=self.finish(self.submit(request));self.assertEqual(a['status'],'FAILED');self.assertEqual(a['attemptCount'],2);self.assertIsNone(a['suggestionId']);self.assertEqual(call('/tasks?date='+request['targetDate'])[1]['total'],0)
 def test_07_auth_is_not_retried_or_reflected(self):
  request,_=self.request('auth');a=self.finish(self.submit(request));self.assertEqual(a['errorCode'],'PROVIDER_AUTH');self.assertEqual(a['attemptCount'],1);self.assertNotIn('do-not-expose',json.dumps(a));self.assertNotIn('test-only-openai',json.dumps(a))
 def test_08_rate_limit_retries_within_deadline(self):
  _,_,a,_=self.success('rate-once');self.assertEqual(a['attemptCount'],2)
 def test_09_timeout_keeps_original_log(self):
  request,log=self.request('timeout');a=self.finish(self.submit(request));self.assertEqual(a['status'],'FAILED');self.assertLess(a['durationMs'],4500);self.assertEqual(call('/study-logs/'+log['id'])[1]['content'],log['content'])
 def test_10_invalid_references_evidence_and_types(self):
  for mode in ['invalid-id','fake-evidence','wrong-type','over-budget']:
   with self.subTest(mode=mode):
    request,_=self.request(mode);a=self.finish(self.submit(request));self.assertEqual(a['status'],'FAILED');self.assertIsNone(a['suggestionId'])
 def test_11_edit_then_concurrent_confirm_creates_one_batch(self):
  req,_,_,s=self.success();proposed=s['tasks'];proposed[0]['title']='人工编辑后的任务';proposed[0]['estimatedMinutes']=25
  code,s=call('/plan-suggestions/'+s['id'],'PATCH',{'version':s['version'],'targetDate':s['targetDate'],'tasks':proposed});self.assertEqual(code,200)
  path='/plan-suggestions/'+s['id']+'/confirm';payload={'version':s['version']}
  with concurrent.futures.ThreadPoolExecutor(max_workers=6) as pool:results=list(pool.map(lambda _:call(path,'POST',payload),range(6)))
  self.assertTrue(all(c==200 for c,r in results),results);self.assertTrue(all(r['taskIds']==results[0][1]['taskIds'] for c,r in results))
  tasks=call('/tasks?date='+req['targetDate'])[1];self.assertEqual(tasks['total'],1);self.assertEqual(tasks['items'][0]['title'],'人工编辑后的任务')
 def test_12_stale_log_blocks_confirmation(self):
  _,log,_,s=self.success();payload={'studyDate':log['studyDate'],'content':'修改后的新原文','durationMinutes':30,'taskIds':[]}
  self.assertEqual(call('/study-logs/'+log['id'],'PATCH',{'version':log['version'],'data':payload})[0],200)
  code,error=call('/plan-suggestions/'+s['id']+'/confirm','POST',{'version':s['version']});self.assertEqual(code,409);self.assertEqual(error['code'],'STALE_CONTEXT')
  code,current=call('/plan-suggestions/'+s['id']);self.assertEqual(code,200,current);self.assertTrue(current['stale'])
 def test_13_reject_blocks_confirmation(self):
  req,_,_,s=self.success();path='/plan-suggestions/'+s['id'];self.assertEqual(call(path+'/reject','POST',{'version':s['version']})[0],200)
  self.assertEqual(call(path+'/confirm','POST',{'version':s['version']})[0],409);self.assertEqual(call('/tasks?date='+req['targetDate'])[1]['total'],0)
 def test_14_rest_day_creates_zero_tasks(self):
  _,_,_,s=self.success('rest',budget=0);code,result=call('/plan-suggestions/'+s['id']+'/confirm','POST',{'version':s['version']});self.assertEqual(code,200);self.assertEqual(result['taskIds'],[])
 def test_15_new_target_tasks_are_included_in_budget(self):
  req,_,_,s=self.success();self.assertEqual(call('/tasks','POST',{'title':'生成后手工安排','scheduledDate':s['targetDate'],'estimateMinutes':50})[0],201)
  code,error=call('/plan-suggestions/'+s['id']+'/confirm','POST',{'version':s['version']});self.assertEqual(code,422);self.assertEqual(error['code'],'BUDGET_EXCEEDED')
  s['tasks'][0]['estimatedMinutes']=10
  code,s=call('/plan-suggestions/'+s['id'],'PATCH',{'version':s['version'],'targetDate':s['targetDate'],'tasks':s['tasks']});self.assertEqual(code,200)
  self.assertEqual(call('/plan-suggestions/'+s['id']+'/confirm','POST',{'version':s['version']})[0],200)
 def test_16_existing_task_is_rescheduled_not_duplicated(self):
  code,t=call('/tasks','POST',{'title':'继续已有任务','scheduledDate':'2035-01-01','estimateMinutes':20});self.assertEqual(code,201)
  req,_,_,s=self.success('existing',task_ids=[t['id']]);code,result=call('/plan-suggestions/'+s['id']+'/confirm','POST',{'version':s['version']});self.assertEqual(code,200);self.assertEqual(result['taskIds'],[t['id']]);self.assertEqual(call('/tasks?date='+req['targetDate'])[1]['total'],1)
 def test_17_regeneration_supersedes_same_context_draft(self):
  request,_,a,s=self.success();new=self.finish(self.submit(request));self.assertEqual(new['status'],'SUCCEEDED');self.assertEqual(call('/plan-suggestions/'+s['id'])[1]['status'],'SUPERSEDED');self.assertEqual(call('/plan-suggestions/'+s['id']+'/confirm','POST',{'version':s['version']})[0],409)
 def test_18_stale_draft_version_is_rejected(self):
  _,_,_,s=self.success();payload={'version':s['version'],'targetDate':s['targetDate'],'tasks':s['tasks']};self.assertEqual(call('/plan-suggestions/'+s['id'],'PATCH',payload)[0],200)
  self.assertEqual(call('/plan-suggestions/'+s['id'],'PATCH',payload)[0],409);self.assertEqual(call('/plan-suggestions/'+s['id']+'/confirm','POST',{'version':payload['version']})[0],409)
 def test_19_confirmation_rolls_back_after_second_insert_fails(self):
  req,_,_,s=self.success();first=s['tasks'][0];first['title']='M2_TX_FIRST';first['estimatedMinutes']=15
  second={**first,'itemId':str(uuid.uuid4()),'title':'M2_TX_FAIL'}
  code,s=call('/plan-suggestions/'+s['id'],'PATCH',{'version':s['version'],'targetDate':s['targetDate'],'tasks':[first,second]});self.assertEqual(code,200)
  env=os.environ.copy();env.update(PGPASSWORD=env['DB_PASSWORD'])
  def sql(text):subprocess.run(['psql','-X','-h','127.0.0.1','-U',env['DB_USER'],'-d','growthhub_test','-v','ON_ERROR_STOP=1','-c',text],env=env,check=True,stdout=subprocess.DEVNULL)
  sql("CREATE FUNCTION m2_test_fail() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.title='M2_TX_FAIL' THEN RAISE EXCEPTION 'test rollback' USING ERRCODE='23514'; END IF; RETURN NEW; END $$; CREATE TRIGGER m2_test_fail BEFORE INSERT ON learning_task FOR EACH ROW EXECUTE FUNCTION m2_test_fail();")
  try:
   code,_=call('/plan-suggestions/'+s['id']+'/confirm','POST',{'version':s['version']});self.assertEqual(code,409)
   self.assertEqual(call('/tasks?date='+req['targetDate'])[1]['total'],0);current=call('/plan-suggestions/'+s['id'])[1];self.assertEqual(current['status'],'DRAFT');self.assertEqual(current['taskIds'],[])
  finally:sql('DROP TRIGGER m2_test_fail ON learning_task; DROP FUNCTION m2_test_fail();')
 def test_20_settings_validation_and_secrets(self):
  code,s=call('/settings');self.assertEqual(code,200);serialized=json.dumps(s);self.assertNotIn('test-only-',serialized);self.assertNotIn('apiKey',serialized)
  data={'version':s['version'],'provider':'OPENAI','model':'test-openai','dailyMinutes':90,'preferences':'只做可验证实践'}
  code,saved=call('/settings','PATCH',data);self.assertEqual(code,200);self.assertEqual(saved['dailyMinutes'],90);self.assertEqual(call('/settings','PATCH',data)[0],409)
 def test_21_invalid_inputs_are_rejected(self):
  req,_=self.request();self.assertEqual(call('/ai/analyses','POST',req)[0],400)
  self.assertEqual(call('/ai/analyses','POST',{**req,'budgetMinutes':-1},str(uuid.uuid4()))[0],400)
  self.assertEqual(call('/ai/analyses?size=100')[0],400)
  self.assertEqual(call('/plan-suggestions/'+str(uuid.uuid4()))[0],404)
if __name__=='__main__':unittest.main(verbosity=2)
