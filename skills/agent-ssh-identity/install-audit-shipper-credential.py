#!/usr/bin/env python3
"""GPL-3.0-or-later. Human-operated hidden-input Atlas credential installer for
any host. Usage: python3 install-audit-shipper-credential.py <ssh-alias>

The selected account must have NOPASSWD sudo on the target, and
provision-forwarder-env.sh must have run there first. The source identity is
derived on the target from hostname+machine-id."""
import getpass
import json
import shlex
import subprocess
import sys

SSH_ALIAS = sys.argv[1] if len(sys.argv) > 1 else None
if not SSH_ALIAS:
    raise SystemExit('Usage: install-audit-shipper-credential.py <ssh-alias>')
if not sys.stdin.isatty():
    raise SystemExit('Run this helper yourself in an interactive terminal.')
password = getpass.getpass('Paste the Atlas knoxx_audit_shipper password (hidden): ')
if not password:
    raise SystemExit('Empty password; nothing changed.')
config = {'uri': 'mongodb+srv://cluster0.abpqhln.mongodb.net/?tls=true',
          'username': 'knoxx_audit_shipper', 'password': password}
# The password crosses SSH stdin only, never argv or the environment.
remote = '''import json,os,pathlib,grp,tempfile,socket
c=json.load(sys.stdin)
c['source']=socket.gethostname()+':'+pathlib.Path('/etc/machine-id').read_text().strip()
directory='/etc/audit-forwarder'
group='audit-shipper'
if os.geteuid()!=0:
    raise SystemExit('must run as root')
if not os.path.isdir(directory) or not grp.getgrnam(group):
    raise SystemExit('run provision-forwarder-env.sh first')
fd,name=tempfile.mkstemp(prefix='.config-',dir=directory)
try:
    os.fchmod(fd,0o640)
    os.fchown(fd,0,grp.getgrnam(group).gr_gid)
    with os.fdopen(fd,'w') as f:
        json.dump(c,f)
        f.flush()
        os.fsync(f.fileno())
    os.replace(name,directory+'/config.json')
finally:
    if os.path.exists(name):os.unlink(name)
print('Credential installed; no secret printed; source='+c['source'])
'''
result = subprocess.run(['ssh', '-o', 'BatchMode=yes', SSH_ALIAS,
                         'sudo -n python3 -c ' + shlex.quote(remote)],
                        input=json.dumps(config), text=True)
password = None
config.clear()
raise SystemExit(result.returncode)
