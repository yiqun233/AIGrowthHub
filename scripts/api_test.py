"""Real PostgreSQL/API regression tests. Run only against the isolated test server."""
import datetime,json,os,unittest,urllib.request,urllib.error,uuid
urllib.request.install_opener(urllib.request.build_opener(urllib.request.ProxyHandler({})))
BASE=os.environ.get('TEST_API_URL','http://127.0.0.1:18080/api/v1')
if ':18080/' not in BASE:raise RuntimeError('Tests must use the isolated 18080 server')
def call(path,method='GET',body=None):
 req=urllib.request.Request(BASE+path,method=method,data=None if body is None else json.dumps(body,ensure_ascii=False).encode(),headers={'Content-Type':'application/json'})
 try:
  with urllib.request.urlopen(req,timeout=10) as res:return res.status,json.load(res)
 except urllib.error.HTTPError as e:
  try:return e.code,json.load(e)
  finally:e.close()
class LearningFlow(unittest.TestCase):
 def goal(self):
  status,g=call('/goals','POST',{'title':'测试目标 '+str(uuid.uuid4()),'description':'验证实际成长','priority':1})
  self.assertEqual(status,201);return g
 def task(self,**changes):
  data={'title':'实践任务 '+str(uuid.uuid4()),'scheduledDate':'2026-10-15','estimateMinutes':30,'acceptanceCriteria':'通过真实接口测试'};data.update(changes)
  status,t=call('/tasks','POST',data);self.assertEqual(status,201);return t
 def test_01_dashboard_timezone_and_empty_query(self):
  status,data=call('/dashboard?date=2026-10-15');self.assertEqual(status,200);self.assertEqual(data['timezone'],'Asia/Shanghai');self.assertEqual(data['date'],'2026-10-15')
  status,items=call('/tasks?date=1900-01-01');self.assertEqual(items['total'],0)
 def test_02_task_completion_reopen_and_stale_version(self):
  t=self.task();status,done=call('/tasks/'+t['id'],'PATCH',{'version':t['version'],'status':'DONE'});self.assertEqual(status,200);self.assertIsNotNone(done['completedAt'])
  status,error=call('/tasks/'+t['id'],'PATCH',{'version':t['version'],'status':'CANCELLED'});self.assertEqual(status,409);self.assertEqual(error['code'],'VERSION_CONFLICT')
  status,reopen=call('/tasks/'+t['id'],'PATCH',{'version':done['version'],'status':'IN_PROGRESS'});self.assertEqual(status,200);self.assertIsNone(reopen['completedAt'])
 def test_03_postpone_keeps_id(self):
  t=self.task();status,new=call('/tasks/'+t['id'],'PATCH',{'version':t['version'],'scheduledDate':'2026-10-20'});self.assertEqual(status,200);self.assertEqual(new['id'],t['id']);self.assertEqual(new['scheduledDate'],'2026-10-20')
 def test_04_task_validation_and_unknown_goal(self):
  status,error=call('/tasks','POST',{'title':'','scheduledDate':'2026-10-15','estimateMinutes':0});self.assertEqual(status,400);self.assertIn('estimateMinutes',error['fieldErrors'])
  status,error=call('/tasks','POST',{'title':'a','scheduledDate':'2026-10-15','estimateMinutes':30,'goalId':str(uuid.uuid4())});self.assertEqual(status,400)
 def test_05_log_preserves_raw_text_and_multiple_same_day(self):
  t=self.task();raw='今天测试：\\n 不修改原文\n  ```java\n  test();\n  ```\n'
  data={'studyDate':'2026-10-15','content':raw,'durationMinutes':0,'taskIds':[t['id']]}
  status,log=call('/study-logs','POST',data);self.assertEqual(status,201);self.assertEqual(log['content'],raw);self.assertEqual(log['taskIds'],[t['id']])
  status,other=call('/study-logs','POST',data);self.assertEqual(status,201);self.assertNotEqual(log['id'],other['id'])
  status,fetched=call('/study-logs/'+log['id']);self.assertEqual(fetched['content'],raw)
 def test_06_log_invalid_reference_is_atomic(self):
  _,before=call('/study-logs?from=1999-01-01&to=1999-01-01')
  status,_=call('/study-logs','POST',{'studyDate':'1999-01-01','content':'invalid','durationMinutes':5,'taskIds':[str(uuid.uuid4())]});self.assertEqual(status,400)
  _,after=call('/study-logs?from=1999-01-01&to=1999-01-01');self.assertEqual(before['total'],after['total'])
 def test_07_log_edit_conflict_and_task_unlink(self):
  t=self.task();data={'studyDate':'2026-10-15','content':'first','durationMinutes':30,'taskIds':[t['id']]};_,log=call('/study-logs','POST',data)
  data.update(content='updated',taskIds=[])
  status,changed=call('/study-logs/'+log['id'],'PATCH',{'version':log['version'],'data':data});self.assertEqual(status,200);self.assertEqual(changed['taskIds'],[])
  status,_=call('/study-logs/'+log['id'],'PATCH',{'version':log['version'],'data':data});self.assertEqual(status,409)
 def test_08_archive_preserves_history_and_rejects_new_association(self):
  g=self.goal();t=self.task(goalId=g['id']);g['status']='ARCHIVED'
  status,archived=call('/goals/'+g['id'],'PATCH',g);self.assertEqual(status,200)
  status,_=call('/tasks','POST',{'title':'new','scheduledDate':'2026-10-15','estimateMinutes':30,'goalId':g['id']});self.assertEqual(status,400)
  status,task=call('/tasks/'+t['id'],'PATCH',{'version':t['version'],'status':'DONE'});self.assertEqual(status,200);self.assertEqual(task['goalId'],g['id'])
 def test_09_pagination_and_bad_inputs(self):
  self.assertEqual(call('/tasks?size=1000')[0],400);self.assertEqual(call('/tasks?date=no-date')[0],400)
  self.assertEqual(call('/study-logs?from=2026-10-20&to=2026-10-01')[0],400);self.assertEqual(call('/study-logs/'+str(uuid.uuid4()))[0],404)
 def test_10_no_task_ids_and_goal_dates(self):
  status,_=call('/study-logs','POST',{'studyDate':'2026-10-15','content':'休息一下','durationMinutes':0});self.assertEqual(status,201)
  status,_=call('/goals','POST',{'title':'bad dates','startDate':'2026-10-20','targetDate':'2026-10-01'});self.assertEqual(status,400)
if __name__=='__main__':unittest.main(verbosity=2)
