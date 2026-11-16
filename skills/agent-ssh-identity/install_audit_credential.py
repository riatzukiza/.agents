#!/usr/bin/env python3
"""GPL-3.0-or-later. Human-operated hidden-input Atlas credential installer."""
import getpass
import json
import subprocess
import sys

if not sys.stdin.isatty():
    raise SystemExit('Run this helper yourself in an interactive terminal.')
password = getpass.getpass('Paste the Atlas knoxx_audit_shipper password (hidden): ')
if not password:
    raise SystemExit('Empty password; nothing changed.')
config = {'uri': 'mongodb+srv://cluster0.abpqhln.mongodb.net/?tls=true',
          'username': 'knoxx_audit_shipper', 'password': password}
# The password crosses SSH stdin only, never argv or the environment.
remote = '''import json,os,pathlib,grp,tempfile,socket,sys
c=json.load(sys.stdin)
c['source']=socket.gethostname()+':'+pathlib.Path('/etc/machine-id').read_text().strip()
directory='/etc/knoxx-audit-forwarder'
fd,name=tempfile.mkstemp(prefix='.config-',dir=directory)
try:
    os.fchmod(fd,0o640)
    os.fchown(fd,0,grp.getgrnam('knoxx-audit-shipper').gr_gid)
    with os.fdopen(fd,'w') as f:
        json.dump(c,f)
        f.flush()
        os.fsync(f.fileno())
    os.replace(name,directory+'/config.json')
finally:
    if os.path.exists(name):os.unlink(name)
print('Credential installed on Knoxx; no secret printed.')
'''
import shlex
result = subprocess.run(['ssh', '-o', 'BatchMode=yes', 'knoxx-agent-codex',
                         'sudo -n python3 -c ' + shlex.quote(remote)],
                        input=json.dumps(config), text=True)
password = None
config.clear()
raise SystemExit(result.returncode)
