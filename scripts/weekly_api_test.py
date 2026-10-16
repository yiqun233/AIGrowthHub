"""Weekly workflow against isolated PostgreSQL, never the personal database."""
import concurrent.futures,datetime,json,os,subprocess,unittest,urllib.request,urllib.error
urllib.request.install_opener(urllib.request.build_opener(urllib.request.ProxyHandler({})))
BASE=os.environ['TEST_API_URL'];assert ':18080/' in BASE
def call(path,method='GET',body=None):
 req=urllib.request.Request(BASE+path,method=method,data=None if body is None else json.dumps(body).encode(),headers={'Content-Type':'application/json'})
 try:
  with urllib.request.urlopen(req,timeout=15) as res:return res.status,json.load(res)
 except urllib.error.HTTPError as e:
  try:return e.code,json.load(e)
  finally:e.close()
def ok(path,method='GET',body=None):
 code,result=call(path,method,body);assert code<300,(code,result);return result
def log(date,content='可追溯的原始学习记录',minutes=30,taskIds=None):return ok('/study-logs','POST',{'studyDate':date,'content':content,'durationMinutes':minutes,'taskIds':taskIds or [],'achievements':'一个可运行成果','blockers':'一个真实阻塞'})
def task(date,status=None):
 t=ok('/tasks','POST',{'title':'周统计任务','scheduledDate':date,'estimateMinutes':45})
 return ok('/tasks/'+t['id'],'PATCH',{'version':t['version'],'status':status}) if status else t
class WeeklyFlow(unittest.TestCase):
 def setUp(self):
  index=int(self._testMethodName.split('_')[1]);self.start=(datetime.date(2060,1,5)+datetime.timedelta(weeks=index)).isoformat()
  assert datetime.date.fromisoformat(self.start).weekday()==0
 def facts(self,date=None):return ok('/weeks?date='+(date or self.start))
 def plan(self):return ok('/weekly-plans','POST',{'weekStart':self.start,'theme':'形成可验证成果','budgetMinutes':120,'notes':'保留原文'})
 def review(self):return ok('/reviews/weekly','POST',{'weekStart':self.start})
 def save(self,r,content='这周完成了接口验证，下周继续处理异常。'):return ok('/reviews/weekly/'+r['id'],'PATCH',{'version':r['version'],'finalContent':content})
 def test_01_monday_year_boundary_and_empty(self):
  f=self.facts('2027-01-03')['facts'];self.assertEqual(f['weekStart'],'2026-12-28');self.assertEqual(f['weekEnd'],'2027-01-03');self.assertEqual(f['timezone'],'Asia/Shanghai')
  f=self.facts();self.assertIsNone(f['review']);self.assertEqual(f['facts']['summary']['actualMinutes'],0)
 def test_02_plan_unique_version_archive(self):
  p=self.plan();self.assertEqual(call('/weekly-plans','POST',{'weekStart':self.start,'theme':'重复','budgetMinutes':0,'notes':''})[0],409)
  body={**p,'status':'ARCHIVED'};updated=ok('/weekly-plans/'+p['id'],'PATCH',body);self.assertEqual(updated['status'],'ARCHIVED')
  self.assertEqual(call('/weekly-plans/'+p['id'],'PATCH',body)[0],409);self.assertEqual(self.facts()['facts']['plan']['id'],p['id'])
 def test_03_week_bounds_cancelled_and_actual_minutes(self):
  start=datetime.date.fromisoformat(self.start);end=(start+datetime.timedelta(days=6)).isoformat()
  log(self.start,minutes=25);log(end,minutes=0);log((start-datetime.timedelta(days=1)).isoformat(),minutes=900)
  task(self.start);task(end,'CANCELLED');summary=self.facts()['facts']['summary']
  self.assertEqual(summary['logCount'],2);self.assertEqual(summary['actualMinutes'],25);self.assertEqual(summary['scheduledMinutes'],45);self.assertEqual(summary['scheduledTaskCount'],1)
 def test_04_snapshot_stale_edit_refresh_and_confirm(self):
  source=log(self.start);r=self.save(self.review());snapshot=r['snapshot'];self.assertFalse(r['stale'])
  ok('/study-logs/'+source['id'],'PATCH',{'version':source['version'],'data':{'studyDate':self.start,'content':'后续编辑的新原文','durationMinutes':60}})
  current=self.facts()['review'];self.assertTrue(current['stale']);self.assertEqual(current['snapshot'],snapshot)
  self.assertEqual(call('/reviews/weekly/'+r['id']+'/confirm','POST',{'version':r['version']})[1]['code'],'STALE_WEEK_REVIEW')
  r=ok('/reviews/weekly/'+r['id']+'/refresh','POST',{'version':r['version']});self.assertEqual(r['finalContent'],current['finalContent']);self.assertFalse(r['stale'])
  confirmed=ok('/reviews/weekly/'+r['id']+'/confirm','POST',{'version':r['version']});self.assertEqual(confirmed['status'],'CONFIRMED')
  again=ok('/reviews/weekly/'+r['id']+'/confirm','POST',{'version':r['version']});self.assertEqual(again['id'],confirmed['id'])
  self.assertEqual(call('/reviews/weekly/'+r['id'],'PATCH',{'version':confirmed['version'],'finalContent':'覆盖历史'})[1]['code'],'REVIEW_READ_ONLY')
  log(self.start,content='确认后的补记');later=self.facts()['review'];self.assertTrue(later['stale']);self.assertEqual(later['snapshot'],confirmed['snapshot'])
 def test_05_new_sources_and_plan_changes_invalidate_draft(self):
  r=self.save(self.review());self.plan();self.assertTrue(self.facts()['review']['stale'])
  r=ok('/reviews/weekly/'+r['id']+'/refresh','POST',{'version':r['version']});task(self.start)
  self.assertEqual(call('/reviews/weekly/'+r['id']+'/confirm','POST',{'version':r['version']})[1]['code'],'STALE_WEEK_REVIEW')
 def test_06_invalid_week_budget_empty_and_missing(self):
  self.assertEqual(call('/weekly-plans','POST',{'weekStart':str(datetime.date.fromisoformat(self.start)+datetime.timedelta(days=1)),'theme':'x','budgetMinutes':1,'notes':''})[1]['code'],'INVALID_WEEK_START')
  self.assertEqual(call('/weekly-plans','POST',{'weekStart':self.start,'theme':' ','budgetMinutes':10081,'notes':''})[0],400)
  r=self.review();self.assertEqual(call('/reviews/weekly/'+r['id']+'/confirm','POST',{'version':r['version']})[1]['code'],'EMPTY_REVIEW')
  self.assertEqual(call('/reviews/weekly/'+r['id'],'PATCH',{'version':999,'finalContent':'x'})[0],409)
  self.assertEqual(call('/weeks?date=not-a-date')[0],400)
 def test_07_cross_week_link_and_shanghai_completion_boundary(self):
  previous=(datetime.date.fromisoformat(self.start)-datetime.timedelta(days=1)).isoformat();t=task(previous)
  log(self.start,taskIds=[t['id']]);env=os.environ.copy();env['PGPASSWORD']=env['DB_PASSWORD']
  # Controlled fixture in the dedicated database; midnight Shanghai is previous-day 16:00 UTC.
  completed=datetime.datetime.combine(datetime.date.fromisoformat(self.start),datetime.time(),tzinfo=datetime.timezone(datetime.timedelta(hours=8))).isoformat()
  sql=f"UPDATE learning_task SET status='DONE',completed_at='{completed}',version=version+1 WHERE id='{t['id']}'"
  subprocess.run(['psql','-X','-h','127.0.0.1','-U',env['DB_USER'],'-d','growthhub_test','-v','ON_ERROR_STOP=1','-c',sql],env=env,check=True,stdout=subprocess.DEVNULL)
  f=self.facts()['facts'];self.assertEqual(f['summary']['scheduledTaskCount'],0);self.assertEqual(f['summary']['completedDuringWeekCount'],1);self.assertEqual(f['linkedTasks'][0]['id'],t['id']);self.assertEqual(f['summary']['actualMinutes'],30)
 def test_08_concurrent_confirm_is_recoverable_and_read_only(self):
  r=self.save(self.review())
  def confirm(_):return call('/reviews/weekly/'+r['id']+'/confirm','POST',{'version':r['version']})
  with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:results=list(pool.map(confirm,range(4)))
  self.assertTrue(all(code in (200,409) for code,_ in results),results)
  final=ok('/reviews/weekly/'+r['id']+'/confirm','POST',{'version':r['version']});self.assertEqual(final['status'],'CONFIRMED')
  self.assertEqual(len({body['id'] for code,body in results if code==200}),1)
 def test_09_export_includes_weekly_records_and_snapshot(self):
  p=self.plan();r=self.save(self.review());export=ok('/exports');self.assertEqual(export['schemaVersion'],'growthhub-export-v4')
  self.assertIn(p['id'],{x['id'] for x in export['data']['weekly_plan']});row=next(x for x in export['data']['weekly_review'] if x['id']==r['id'])
  self.assertNotIn('source_hash',row);self.assertEqual(row['source_snapshot']['weekStart'],self.start);self.assertEqual(export['counts']['weekly_review'],len(export['data']['weekly_review']))
if __name__=='__main__':unittest.main(verbosity=2)
