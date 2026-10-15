"""Read one-line dotenv literals consistently; never execute shell substitutions."""
from pathlib import Path
import os,re,shlex,sys
def load_env(path,base=None):
 env=dict(os.environ if base is None else base)
 for number,line in enumerate(Path(path).read_text(encoding='utf-8-sig').splitlines(),1):
  line=line.strip()
  if not line or line.startswith('#'):continue
  if line.startswith('export '):line=line[7:].lstrip()
  key,sep,raw=line.partition('=');key=key.strip();raw=raw.strip()
  try:
   if not sep or not re.fullmatch(r'[A-Za-z_][A-Za-z0-9_]*',key):raise ValueError()
   if raw.startswith(('"',"'")):
    values=shlex.split(raw,comments=True,posix=True)
    if len(values)!=1:raise ValueError()
    value=values[0]
   else:value=raw.split(' #',1)[0].rstrip()
   if '\0' in value:raise ValueError()
  except ValueError:raise ValueError(f'{Path(path).name}:{number}: 配置格式无效，请使用单行 KEY=value 或引号包裹的值') from None
  env[key]=value
 return env
if __name__=='__main__':
 try:
  values=load_env(sys.argv[1],{})
  if '--null' in sys.argv:
   for key,value in values.items():sys.stdout.buffer.write(key.encode()+b'\0'+value.encode()+b'\0')
 except (ValueError,OSError) as e:print(str(e),file=sys.stderr);raise SystemExit(1)
