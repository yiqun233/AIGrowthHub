import concurrent.futures,datetime,json,os,time,unittest,urllib.request,urllib.error,uuid
urllib.request.install_opener(urllib.request.build_opener(urllib.request.ProxyHandler({})))
BASE=os.environ['TEST_API_URL'];assert ':18080/' in BASE
def call(path,method='GET',body=None,key=None):
 headers={'Content-Type':'application/json'}
 if key:headers['Idempotency-Key']=key
 req=urllib.request.Request(BASE+path,method=method,data=None if body is None else json.dumps(body).encode(),headers=headers)
 try:
  with urllib.request.urlopen(req,timeout=30) as res:return res.status,json.load(res)
 except urllib.error.HTTPError as e:
  try:return e.code,json.load(e)
  finally:e.close()
def ok(path,method='GET',body=None,key=None):
 code,value=call(path,method,body,key);assert code<300,(code,value);return value
def log(date,content='实际的周复盘证据原文',tasks=None):return ok('/study-logs','POST',{'studyDate':date,'content':content,'durationMinutes':20,'taskIds':tasks or []})
def task(date):return ok('/tasks','POST',{'title':'周任务归属验证','scheduledDate':date,'estimateMinutes':20})
def monday(offset):
 date=datetime.date(2070,1,6);date-=datetime.timedelta(days=date.weekday());return str(date+datetime.timedelta(weeks=offset))
def plan(date):return ok('/weekly-plans','POST',{'weekStart':date,'theme':'周归属验证','budgetMinutes':60,'notes':''})
def wait(job):
 for _ in range(150):
  current=ok('/ai/works/'+job['id'])
  if current['status'] in ('SUCCEEDED','FAILED'):return current
  time.sleep(.1)
 raise AssertionError('AI work did not finish')
def work(path,version,provider='OPENAI',key=None):return ok(path,'POST',{'version':version,'provider':provider,'model':'test-'+provider.lower(),'consent':True},key or str(uuid.uuid4()))
class Remaining(unittest.TestCase):
 def test_01_achievement_create_archive_validation(self):
  data={'title':'可验证交付','description':'通过测试的代码','url':'https://example.org/code','kind':'CODE','achievementDate':monday(1)}
  a=ok('/achievements','POST',data);archived=ok('/achievements/'+a['id'],'PATCH',{'version':a['version'],'data':data,'archived':True})
  self.assertIn(a['id'],[x['id'] for x in ok('/achievements?archived=true')['items']]);self.assertEqual(call('/achievements/'+a['id'],'PATCH',{'version':a['version'],'data':data,'archived':False})[0],409)
  self.assertEqual(call('/achievements','POST',{**data,'url':'javascript:alert(1)'})[0],400);self.assertEqual(call('/achievements','POST',{**data,'taskId':str(uuid.uuid4())})[0],400)
  self.assertFalse(ok('/achievements/'+a['id'],'PATCH',{'version':archived['version'],'data':data,'archived':False})['archived'])
 def test_02_assignment_guard_and_atomic_transfer(self):
  p=plan(monday(2));other=plan(monday(3));t=task(monday(2))
  assigned=ok('/tasks/'+t['id']+'/weekly-plan','PATCH',{'version':t['version'],'weeklyPlanId':p['id']});self.assertEqual(assigned['weeklyPlanId'],p['id'])
  self.assertEqual(call('/tasks/'+t['id'],'PATCH',{'version':assigned['version'],'scheduledDate':monday(3)})[0],409)
  moved=ok('/tasks/'+t['id']+'/weekly-plan','PATCH',{'version':assigned['version'],'weeklyPlanId':other['id'],'scheduledDate':monday(3)})
  self.assertEqual(moved['weeklyPlanId'],other['id']);self.assertIsNone(ok('/tasks/'+t['id']+'/weekly-plan','PATCH',{'version':moved['version'],'weeklyPlanId':None})['weeklyPlanId'])
  self.assertEqual(call('/tasks/'+t['id']+'/weekly-plan','PATCH',{'version':moved['version'],'weeklyPlanId':p['id']})[0],409)
 def test_03_weekly_ai_evidence_apply_and_stale(self):
  source=log(monday(4));r=ok('/reviews/weekly','POST',{'weekStart':monday(4)});key=str(uuid.uuid4());path='/reviews/weekly/'+r['id']+'/generate'
  job=work(path,r['version'],key=key);self.assertEqual(work(path,r['version'],key=key)['id'],job['id']);done=wait(job);self.assertEqual(done['status'],'SUCCEEDED',done)
  self.assertEqual(done['result']['evidence'][0]['sourceId'],source['id']);self.assertEqual(done['inputTokens'],100)
  applied=ok('/reviews/weekly/'+r['id']+'/apply-ai','POST',{'version':r['version'],'workId':job['id']});self.assertIsNotNone(applied['aiDraft']);self.assertIn('真实来源',applied['finalContent'])
  log(monday(4),'补记会使来源失效');self.assertEqual(call('/reviews/weekly/'+r['id']+'/apply-ai','POST',{'version':applied['version'],'workId':job['id']})[1]['code'],'STALE_CONTEXT')
 def test_04_feed_collection_dedup_and_safe_xml_failure(self):
  sources=ok('/feed/refresh','POST');self.assertTrue(all(s['last_success_at'] for s in sources));first=ok('/feed/items');self.assertEqual(first['total'],2)
  self.assertEqual(call('/feed/refresh','POST')[0],429);time.sleep(10.2);ok('/feed/refresh','POST');self.assertEqual(ok('/feed/items')['total'],2)
  with urllib.request.urlopen('http://127.0.0.1:18889/fixture-mode?bad=1'):pass
  time.sleep(10.2);failed=ok('/feed/refresh','POST');self.assertTrue(all(s['error_code']=='FEED_UNAVAILABLE' for s in failed));self.assertEqual(ok('/feed/items')['total'],2)
  self.assertEqual(call('/dashboard')[0],200)
  with urllib.request.urlopen('http://127.0.0.1:18889/fixture-mode?bad=0'):pass
 def test_05_feed_favorite_ai_and_confirm_once(self):
  item=ok('/feed/items')['items'][0];fav=ok('/feed/items/'+item['id'],'PATCH',{'version':item['version'],'favorite':True});self.assertTrue(ok('/feed/items?favorite=true')['items'])
  job=wait(work('/feed/items/'+item['id']+'/summarize',fav['version'],'GEMINI'));self.assertEqual(job['status'],'SUCCEEDED',job)
  data={'title':'验证资讯发布变化','detail':'只在确认后创建正式任务','acceptanceCriteria':'保留测试结果','estimateMinutes':20,'scheduledDate':monday(5)}
  c=ok('/feed/items/'+item['id']+'/candidate','POST',data);self.assertIsNone(c['taskId']);self.assertEqual(ok('/feed/items/'+item['id']+'/candidate','POST',data)['id'],c['id'])
  edited=ok('/feed/candidates/'+c['id'],'PATCH',{'version':c['version'],'data':{**data,'title':'人工编辑的资讯候选'}})
  def confirm(_):return call('/feed/candidates/'+c['id']+'/confirm','POST',{'version':edited['version']})
  with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:results=list(pool.map(confirm,range(4)))
  self.assertTrue(all(code==200 for code,_ in results),results);self.assertEqual(len({x['taskId'] for _,x in results}),1)
 def test_06_invalid_evidence_auth_failure_and_consent(self):
  for offset,content,expected in [(6,'[test:invalid] 原始来源','INVALID_AI_OUTPUT'),(7,'[test:auth] 来源保留','PROVIDER_AUTH')]:
   log(monday(offset),content);r=ok('/reviews/weekly','POST',{'weekStart':monday(offset)});path='/reviews/weekly/'+r['id']+'/generate'
   self.assertEqual(call(path,'POST',{'version':r['version'],'provider':'OPENAI','model':'test-openai','consent':False},str(uuid.uuid4()))[0],400)
   done=wait(work(path,r['version']));self.assertEqual(done['status'],'FAILED',done);self.assertEqual(done['errorCode'],expected);self.assertNotIn('do-not-expose',json.dumps(done))
   self.assertEqual(ok('/reviews/weekly/'+r['id'])['finalContent'],'')
 def test_07_global_ai_activity_and_no_automatic_tasks(self):
  source=log(monday(8),'[test:timeout] 原始记录');r=ok('/reviews/weekly','POST',{'weekStart':monday(8)});before=ok('/tasks?size=1')['total'];job=work('/reviews/weekly/'+r['id']+'/generate',r['version'])
  code,error=call('/ai/analyses','POST',{'sourceLogId':source['id'],'targetDate':monday(9),'budgetMinutes':60,'provider':'OPENAI','model':'test-openai','feedback':''},str(uuid.uuid4()))
  self.assertEqual(code,409,error);self.assertEqual(wait(job)['status'],'FAILED');self.assertEqual(ok('/tasks?size=1')['total'],before)
 def test_08_export_covers_all_entities_without_credentials(self):
  export=ok('/exports');self.assertEqual(export['schemaVersion'],'growthhub-export-v4');self.assertEqual(len(export['data']),17)
  for t,count in export['counts'].items():self.assertEqual(count,len(export['data'][t]))
  for w in export['data']['ai_work']:self.assertNotIn('request_key',w);self.assertNotIn('source_hash',w)
  self.assertNotIn('test-only-',json.dumps(export))
 def test_09_timezone_and_activity_totals(self):
  saved=ok('/settings')
  def settings(timezone):
   current=ok('/settings');return call('/settings','PATCH',{k:current[k] for k in ['version','provider','model','dailyMinutes','preferences']}|{'timezone':timezone})
  try:
   self.assertEqual(settings('not/a-zone')[0],400);self.assertEqual(settings('+09:30')[0],400);self.assertEqual(settings('UTC')[0],200);self.assertEqual(ok('/weeks?date='+monday(10))['facts']['timezone'],'UTC')
   for kind,minutes in [('LEARNING',20),('DEVELOPMENT',35),('REST',0)]:ok('/study-logs','POST',{'studyDate':monday(10),'content':'分类实际时长 '+kind,'durationMinutes':minutes,'activityType':kind})
   summary=ok('/weeks?date='+monday(10))['facts']['summary'];self.assertEqual((summary['actualMinutes'],summary['learningMinutes'],summary['developmentMinutes']),(55,20,35))
   self.assertEqual(call('/study-logs','POST',{'studyDate':monday(10),'content':'非法休息时长','durationMinutes':10,'activityType':'REST'})[0],400)
  finally:self.assertEqual(settings(saved['timezone'])[0],200)
 def test_10_daily_ai_cannot_silently_move_assigned_task(self):
  p=plan(monday(11));t=task(monday(11));assigned=ok('/tasks/'+t['id']+'/weekly-plan','PATCH',{'version':t['version'],'weeklyPlanId':p['id']});source=log(monday(11),tasks=[t['id']])
  job=ok('/ai/analyses','POST',{'sourceLogId':source['id'],'targetDate':monday(12),'budgetMinutes':60,'provider':'OPENAI','model':'test-openai','feedback':'[test:existing]'},str(uuid.uuid4()))
  for _ in range(100):
   daily=ok('/ai/analyses/'+job['id'])
   if daily['status'] in ('SUCCEEDED','FAILED'):break
   time.sleep(.1)
  self.assertEqual(daily['status'],'SUCCEEDED',daily);suggestion=ok('/plan-suggestions/'+daily['suggestionId'])
  self.assertEqual(call('/plan-suggestions/'+suggestion['id']+'/confirm','POST',{'version':suggestion['version']})[0],409)
  current=next(x for x in ok('/tasks?date='+monday(11))['items'] if x['id']==t['id']);self.assertEqual(current['version'],assigned['version']);self.assertEqual(ok('/plan-suggestions/'+suggestion['id'])['status'],'DRAFT')
if __name__=='__main__':unittest.main(verbosity=2)
