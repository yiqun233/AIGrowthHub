"""Local HTTP provider fixture. Imported only by the isolated test runner."""
import collections,json,re,threading,time,uuid
from http.server import BaseHTTPRequestHandler,ThreadingHTTPServer
class StubServer(ThreadingHTTPServer):
 daemon_threads=True
 def __init__(self):
  super().__init__(('127.0.0.1',18889),Handler);self.counts=collections.Counter();self.lock=threading.Lock()
class Handler(BaseHTTPRequestHandler):
 def log_message(self,*args):pass
 def reply(self,code,body,headers=None):
  payload=json.dumps(body,ensure_ascii=False).encode()
  self.send_response(code);self.send_header('Content-Type','application/json');self.send_header('Content-Length',str(len(payload)))
  for key,value in (headers or {}).items():self.send_header(key,value)
  self.end_headers()
  try:self.wfile.write(payload)
  except (BrokenPipeError,ConnectionResetError):pass
 def do_GET(self):
  if self.path.startswith('/fixture-mode'):
   self.server.bad_feed='bad=1' in self.path;return self.reply(200,{'ok':True})
  if self.path.startswith('/feeds/'):
   bad=getattr(self.server,'bad_feed',False)
   body='<!DOCTYPE feed [<!ENTITY xxe SYSTEM "file:///etc/passwd">]><feed xmlns="http://www.w3.org/2005/Atom"><title>&xxe;</title></feed>' if bad else '<feed xmlns="http://www.w3.org/2005/Atom"><title>Official release fixture</title><entry><id>'+self.path+'</id><title>Release fixture '+self.path+'</title><link href="https://example.org'+self.path+'?utm_source=test"/><updated>2026-10-15T00:00:00Z</updated><content>Official release notes for a verifiable practice task.</content></entry></feed>'
   payload=body.encode();self.send_response(200);self.send_header('Content-Type','application/atom+xml');self.send_header('Content-Length',str(len(payload)));self.end_headers();self.wfile.write(payload);return
  return self.reply(404,{})
 def ai_reply(self,provider,content):
  if provider=='ANTHROPIC':return self.reply(200,{'stop_reason':'end_turn','content':[{'type':'thinking','thinking':'ignore'},{'type':'text','text':content}],'usage':{'input_tokens':100,'output_tokens':30}})
  if provider=='GEMINI':return self.reply(200,{'candidates':[{'finishReason':'STOP','content':{'parts':[{'text':'ignore','thought':True},{'text':content}]}}],'usageMetadata':{'promptTokenCount':100,'candidatesTokenCount':30}})
  return self.reply(200,{'choices':[{'finish_reason':'stop','message':{'content':content}}],'usage':{'prompt_tokens':100,'completion_tokens':30}})
 def do_POST(self):
  data=json.loads(self.rfile.read(int(self.headers['Content-Length'])))
  provider=self.path.split('/')[1].upper()
  if provider=='ANTHROPIC':
   if self.headers.get('x-api-key')!='test-only-anthropic' or self.headers.get('anthropic-version')!='2023-06-01' or not self.path.endswith('/messages') or not data.get('system') or data.get('max_tokens')!=4096:return self.reply(400,{'error':'Claude wire contract'})
   context=json.loads(data['messages'][-1]['content'])
  elif provider=='GEMINI':
   if self.headers.get('x-goog-api-key')!='test-only-gemini' or not self.path.endswith(':generateContent') or not data.get('systemInstruction') or data.get('generationConfig',{}).get('responseMimeType')!='application/json':return self.reply(400,{'error':'Gemini wire contract'})
   context=json.loads(data['contents'][0]['parts'][0]['text'])
  else:
   if self.headers.get('Authorization')!='Bearer test-only-'+provider.lower():return self.reply(401,{'error':'missing test authentication'})
   mode='json_schema' if provider=='OPENAI' else 'json_object'
   token='max_completion_tokens' if provider in ('OPENAI','DOUBAO') else 'max_tokens'
   if (provider!='CUSTOM' and data.get('response_format',{}).get('type')!=mode) or token not in data or not self.path.endswith('/chat/completions'):return self.reply(400,{'error':'wrong adapter request contract'})
   context=json.loads(data['messages'][-1]['content'])
  if 'source' not in context:
   text=json.dumps(context,ensure_ascii=False)
   if '[test:auth]' in text:return self.reply(401,{'error':'do-not-expose-this-body'})
   if '[test:timeout]' in text:time.sleep(5)
   refs=context.get('logs',[])+context.get('scheduledTasks',[]) if 'weekStart' in context else [context]
   evidence=[]
   if refs:
    ref=refs[0];quote=ref.get('content') or ref.get('title');evidence=[{'sourceId':ref['id'],'quote':quote[:80]}]
   if '[test:invalid]' in text:evidence=[{'sourceId':str(uuid.uuid4()),'quote':'invented evidence'}]
   result={'summary':'基于真实来源形成的周复盘或资讯摘要。','evidence':evidence,'nextSteps':['实现一个小验证并保留测试结果。'],'limitations':'仅基于当前记录，不代表能力认证。'}
   return self.ai_reply(provider,json.dumps(result,ensure_ascii=False))
  source=context['source'];feedback=context.get('feedback','')
  match=re.search(r'\[test:([a-z-]+)\]',feedback);mode=match.group(1) if match else 'valid'
  key=(source['id'],mode)
  with self.server.lock:self.server.counts[key]+=1;attempt=self.server.counts[key]
  if mode=='auth':return self.reply(401,{'error':'do-not-expose-this-provider-body test-only-openai'})
  if mode=='rate-once' and attempt==1:return self.reply(429,{'error':'slow down'},{'Retry-After':'1'})
  if mode=='timeout':time.sleep(5)
  if mode=='slow':time.sleep(.8)
  goal=context['goals'][0]['id'] if context['goals'] else None
  candidate={'title':'验证一个可运行的小成果','reason':'根据今天的记录继续实践','estimatedMinutes':20,'acceptanceCriteria':'保留一条测试结果和可运行代码','goalId':goal,'existingTaskId':None}
  if mode=='existing':
   ids=source.get('taskIds',[]);task=next((t for t in context['openTasks'] if t['id'] in ids),None)
   if not task:return self.reply(400,{'error':'test source association missing in context'})
   candidate.update(title=task['title'],existingTaskId=task['id'],goalId=task['goalId'])
  if mode=='over-budget':candidate['estimatedMinutes']=context['budgetMinutes']+1
  if mode=='invalid-id':candidate['goalId']=str(uuid.uuid4())
  result={'summary':'根据保存的实践记录形成了下一步建议。','learnedTopics':['AI 工程实践'],'evidence':[{'logId':source['id'],'quote':source['content'][:80]}],
   'openQuestions':['下一步如何验证异常分支？'],'masteryAssessment':'这仅是模型推断，还需要用测试与实际成果验证。','tomorrowTasks':[] if mode=='rest' else [candidate],'planAdjustmentReason':'控制学习量并优先完成实践验证。'}
  if mode=='fake-evidence':result['evidence'][0]['quote']='这段证据在原始记录中并不存在'
  if mode=='wrong-type':candidate['estimatedMinutes']='20'
  content='{' if mode=='always-invalid' or (mode=='invalid-once' and attempt==1) else json.dumps(result,ensure_ascii=False)
  return self.ai_reply(provider,content)

def start():
 server=StubServer();thread=threading.Thread(target=server.serve_forever,daemon=True);thread.start();return server
