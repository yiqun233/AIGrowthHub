from pathlib import Path
import os,subprocess,tempfile,time
ROOT=Path(__file__).resolve().parents[1];env=os.environ.copy()
from local_env import load_env
env=load_env(ROOT/'.env',env)
assert env.get('DB_USER')=='growthhub'
root=Path(tempfile.mkdtemp(prefix='retention-',dir=ROOT/'backups'));(root/'daily').mkdir();(root/'weekly').mkdir()
old=[]
for part,days in [('daily',9),('weekly',35)]:
 p=root/part/'growthhub-old.dump';p.write_text('owned retention fixture');age=time.time()-days*86400;os.utime(p,(age,age));old.append(p)
untouched=root/'daily'/'keep-this.txt';untouched.write_text('must remain')
env.update(PGHOST='127.0.0.1',PGDATABASE='growthhub_test',PGUSER=env['DB_USER'],PGPASSWORD=env['DB_PASSWORD'],BACKUP_ONCE='true',BACKUP_ROOT=str(root))
subprocess.run(['bash','infra/backup-loop.sh'],cwd=ROOT,env=env,check=True)
assert not any(p.exists() for p in old);assert untouched.exists()
files=list(root.rglob('*.dump'));assert len(files)==2
for file in files:subprocess.run(['pg_restore','--list',str(file)],check=True,stdout=subprocess.DEVNULL)
weekly=next((root/'weekly').glob('*.dump'));weekly.write_text('corrupt weekly fixture')
subprocess.run(['bash','infra/backup-loop.sh'],cwd=ROOT,env=env,check=True);subprocess.run(['pg_restore','--list',str(weekly)],check=True,stdout=subprocess.DEVNULL)
# Simulate a corrupt file for the current day: it must be replaced by a valid archive.
daily=next((root/'daily').glob('*.dump'));daily.write_text('corrupt fixture')
subprocess.run(['bash','infra/backup-loop.sh'],cwd=ROOT,env=env,check=True);subprocess.run(['pg_restore','--list',str(daily)],check=True,stdout=subprocess.DEVNULL)
failure=Path(tempfile.mkdtemp(prefix='retention-failure-',dir=ROOT/'backups'));(failure/'daily').mkdir();prior=failure/'daily'/'growthhub-prior.dump';prior.write_bytes(daily.read_bytes());age=time.time()-9*86400;os.utime(prior,(age,age))
env.update(PGPORT='1',PGCONNECT_TIMEOUT='2',BACKUP_ROOT=str(failure));result=subprocess.run(['bash','infra/backup-loop.sh'],cwd=ROOT,env=env,stdout=subprocess.PIPE,stderr=subprocess.PIPE)
assert result.returncode!=0 and prior.exists()
print('Daily/weekly retention, archive validation, corrupt-file repair and failure preservation passed.')
