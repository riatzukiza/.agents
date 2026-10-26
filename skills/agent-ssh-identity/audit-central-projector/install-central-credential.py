#!/usr/bin/env python3
"""GPL-3.0-or-later. Human-operated hidden-input installer for the central
audit reader credential. Run OUTSIDE any agent session, in your own terminal:

    python3 install-central-credential.py

Paste the password of the Atlas DB user you created with read-only access to
linux_audit.events only (custom role scoped to that collection is preferred).
Nothing is printed but a success line."""
import getpass
import json
import shlex
import subprocess
import sys

if not sys.stdin.isatty():
    raise SystemExit('Run this helper yourself in an interactive terminal.')
password = getpass.getpass('Paste the central audit reader password (hidden): ')
if not password:
    raise SystemExit('Empty password; nothing changed.')
config = {'uri': 'mongodb+srv://cluster0.abpqhln.mongodb.net/?tls=true',
          'username': 'central_audit_reader', 'password': password}
remote = '''import json,os,tempfile
if os.geteuid()!=0: raise SystemExit('must run as root')
c=json.load(sys.stdin)
c['uri']='mongodb+srv://cluster0.abpqhln.mongodb.net/?tls=true'
fd,name=tempfile.mkstemp(prefix='.config-',dir='/etc/audit-central-projector')
try:
    os.fchmod(fd,0o600)
    with os.fdopen(fd,'w') as f:
        json.dump(c,f); f.flush(); os.fsync(f.fileno())
    os.replace(name,'/etc/audit-central-projector/config.json')
finally:
    if os.path.exists(name): os.unlink(name)
print('Central reader credential installed; no secret printed.')
'''
import shlex
result = subprocess.run(['ssh', '-o', 'BatchMode=yes', 'knoxx',
                         'sudo -n python3 -c ' + shlex.quote(remote)],
                        input=json.dumps(config), text=True)
password = None
config.clear()
raise SystemExit(result.returncode)
