"""Build/start/test/stop a dedicated local deployment; never remove its data volumes."""
from pathlib import Path
import os,secrets,subprocess,shutil,sys
ROOT=Path(__file__).resolve().parents[1];runtime=ROOT/'.runtime';runtime.mkdir(exist_ok=True)
file=runtime/'container-test.env';os.umask(0o077)
if not file.exists():
 file.write_text('PG_ADMIN_PASSWORD='+secrets.token_urlsafe(32)+'\nDB_PASSWORD='+secrets.token_urlsafe(32)+'\nAUTH_USERNAME=container-test\nAUTH_PASSWORD='+secrets.token_urlsafe(32)+'\nAPP_DOMAIN=localhost\nACME_EMAIL=operator@example.com\nBIND_ADDRESS=127.0.0.1\nHTTP_PORT=18085\nHTTPS_PORT=18443\nBASE_REGISTRY='+os.environ.get('BASE_REGISTRY','docker.io/library')+'\n')
values=dict(line.split('=',1) for line in file.read_text().splitlines() if line and not line.startswith('#'))
if os.environ.get('BASE_REGISTRY'):
 values['BASE_REGISTRY']=os.environ['BASE_REGISTRY'];file.write_text(''.join(k+'='+v+'\n' for k,v in values.items()))
assert values['BIND_ADDRESS']=='127.0.0.1' and values['APP_DOMAIN']=='localhost'
assert values['HTTP_PORT']=='18085' and values['HTTPS_PORT']=='18443'
docker=os.environ.get('DOCKER_BIN',shutil.which('docker') or 'docker')
def path(p):return subprocess.check_output(['wslpath','-w',str(p)],text=True).strip() if docker.endswith('.exe') else str(p)
compose=[docker,'compose','--project-name','growthhub-release-check','--env-file',path(file),'-f',path(ROOT/'infra/compose.prod.yml'),'-f',path(ROOT/'infra/compose.local.yml')]
try:
 subprocess.run(compose+['up','-d','--build','--wait','--wait-timeout','180'],cwd=ROOT,check=True)
 subprocess.run([sys.executable,'scripts/container_test.py'],cwd=ROOT,check=True)
finally:subprocess.run(compose+['down'],cwd=ROOT,check=True)
