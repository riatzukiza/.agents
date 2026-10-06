from pathlib import Path
import json, subprocess, tempfile, shutil, hashlib

source = Path(__file__).resolve().parents[3]
fixture = Path(tempfile.mkdtemp(prefix='receipt-helper-isolation-fixture-'))
results = []

def run(args, cwd):
    return subprocess.run(args, cwd=cwd, text=True, capture_output=True)

def check(name, condition, **evidence):
    results.append({'case': name, 'passed': bool(condition), **evidence})

def git(*args, cwd):
    p=run(['git',*args],cwd)
    if p.returncode: raise RuntimeError(p.stderr)

def discover(relative, start):
    expr='(load-file (first *command-line-args*)) (prn (' + relative + '.common/find-project-root (second *command-line-args*)))'
    p=run(['bb','-e',expr,str(source/'skills'/relative/'scripts/common.bb'),str(start)],fixture)
    if p.returncode: return {'exit':p.returncode,'error':p.stderr[:200]}
    return json.loads(p.stdout.strip().splitlines()[-1])

def append(cwd, extra):
    args=['bb',str(source/'skills/receipt-river/scripts/rr-append.bb'),'--kind',':observation','--origin','isolated-fixture','--dod','fixture validation','--pi','fixture',*extra]
    return run(args,cwd)

try:
    ordinary=fixture/'ordinary';ordinary.mkdir()
    git('init','-q','--initial-branch=main',cwd=ordinary)
    git('-c','user.name=Fixture','-c','user.email=fixture@example.invalid','commit','--allow-empty','-qm','fixture',cwd=ordinary)
    check('ordinary receipt root',discover('receipt-river',ordinary)==str(ordinary))
    check('ordinary mycology root',discover('session-mycology',ordinary)==str(ordinary))
    for name, extra, expected_manifest, expected_refs in [
        ('omitted collections',[],[],[]),
        ('CSV collections with spaces',['--manifest','src/file with spaces.clj, docs/note.md','--refs','abc123, issue-19'],['src/file with spaces.clj','docs/note.md'],['abc123','issue-19']),
        ('EDN vector collections',['--manifest','["comma,path.clj" "space path.clj"]','--refs','["issue-19"]'],['comma,path.clj','space path.clj'],['issue-19'])
    ]:
        case=fixture/name.replace(' ','-');(case/'.ημ').mkdir(parents=True)
        ledger=case/'.ημ/receipts.edn';prefix='{:fixture "historical-prefix"}\n';ledger.write_text(prefix)
        p=append(case,extra)
        expr='(require \'[clojure.edn :as e] \'[clojure.string :as s] \'[cheshire.core :as j]) (println (j/generate-string (e/read-string (last (s/split-lines (slurp (first *command-line-args*)))))))'
        q=run(['bb','-e',expr,str(ledger)],fixture);receipt=json.loads(q.stdout)
        check(name,p.returncode==0 and receipt.get('manifest')==expected_manifest and receipt.get('refs')==expected_refs,manifest=receipt.get('manifest'),refs=receipt.get('refs'))
        check(name+' prefix preserved',ledger.read_bytes().startswith(prefix.encode()))
    ancestor=fixture/'ancestor';(ancestor/'.ημ/session-mycology').mkdir(parents=True)
    ancestor_ledger=ancestor/'.ημ/receipts.edn';ancestor_ledger.write_text('{:fixture "ancestor-sentinel"}\n')
    ancestor_reflection=ancestor/'.ημ/session-mycology/ledger.md';ancestor_reflection.write_text('ancestor reflection sentinel\n')
    prefix=ancestor_ledger.read_bytes();reflection_prefix=ancestor_reflection.read_bytes()
    worktree=ancestor/'nested/linked';worktree.parent.mkdir()
    git('worktree','add','--quiet','--detach',str(worktree),'HEAD',cwd=ordinary)
    nested=worktree/'deeper/work';nested.mkdir(parents=True)
    for skill in ['receipt-river','session-mycology']:
        selected=discover(skill,nested)
        check(skill.split('/')[0]+' linked worktree boundary',selected==str(worktree),selected=selected,expected=str(worktree))
    p=run(['bb',str(source/'skills/receipt-river/scripts/rr-init.bb'),'--eta-mu'],nested)
    p=append(nested,[])
    check('append owns linked worktree ledger',(worktree/'.ημ/receipts.edn').exists(),exit=p.returncode)
    check('ancestor receipt bytes unchanged',ancestor_ledger.read_bytes()==prefix,original_sha256=hashlib.sha256(prefix).hexdigest(),after_sha256=hashlib.sha256(ancestor_ledger.read_bytes()).hexdigest())
    p=run(['bb',str(source/'skills/session-mycology/scripts/sm-log.bb'),'--task','isolated fixture','--note','no shared state'],nested)
    check('mycology owns linked worktree ledger',(worktree/'.ημ/session-mycology/ledger.md').exists(),exit=p.returncode)
    check('ancestor mycology bytes unchanged',ancestor_reflection.read_bytes()==reflection_prefix)
    invalid=ancestor/'invalid';invalid.mkdir();(invalid/'.git').write_text('gitdir: /nonexistent/fixture-git-dir\n')
    for skill in ['receipt-river','session-mycology']:
        selected=discover(skill,invalid)
        check(skill.split('/')[0]+' invalid boundary refuses ancestor',isinstance(selected,dict) and selected.get('exit',0)!=0,selected=selected)
    print(json.dumps({'fixture':str(fixture),'source':str(source),'results':results,'failed':sum(not x['passed'] for x in results)},indent=2))
finally:
    shutil.rmtree(fixture)
