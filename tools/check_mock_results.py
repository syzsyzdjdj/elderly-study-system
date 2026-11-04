"""Independently recompute Spark aggregates and ItemCF using Python standard library."""
import csv, json, math, re, sys
from pathlib import Path
from collections import defaultdict, Counter

root = Path(sys.argv[1])
def read(path, delimiter=','):
    with path.open(encoding='utf-8-sig', newline='') as f:
        return list(csv.DictReader(f, delimiter=delimiter, quoting=csv.QUOTE_MINIMAL if delimiter == ',' else csv.QUOTE_NONE))
def out(name): return read(root/'out'/name/'part-00000', '\t')
logs = read(root/'in/study_log.csv')
courses = {int(c['id']): c for c in read(root/'in/course_info.csv')}
questions = read(root/'in/ai_question.csv')
for r in logs:
    for k in ('user_id','course_id','study_time','progress','is_finish'): r[k] = int(r[k])
    assert r['user_id'] > 0 and r['course_id'] > 0 and r['study_time'] >= 0
    assert 0 <= r['progress'] <= 100 and r['is_finish'] in (0,1)
    assert r['study_time'] or r['progress'] or r['is_finish']
checks = []
def check(name, condition):
    checks.append({'check':name,'passed':bool(condition)})
    if not condition: raise AssertionError(name)
baskets = defaultdict(set); hot = defaultdict(set); categories = defaultdict(set)
days = defaultdict(set); minutes = Counter()
for r in logs:
    u,c,d=r['user_id'],r['course_id'],r['study_date']
    baskets[u].add(c); hot[c].add(u); categories[courses[c]['category_name']].add(u)
    days[d].add(u); minutes[d]+=r['study_time']
check('course hot exact distinct users and ranking',[(int(r['course_id']),int(r['study_count'])) for r in out('course_hot')] == sorted(((c,len(us)) for c,us in hot.items()),key=lambda x:(-x[1],x[0])))
check('daily active users and incremental minutes exact',{r['study_date']:(int(r['study_count']),int(r['study_duration_min'])) for r in out('study_trend')} == {d:(len(us),minutes[d]) for d,us in days.items()})
denom = sum(map(len,categories.values()))
shares = out('course_category')
check('category user counts and participation percentages exact',len(shares)==len(categories) and all(int(r['study_count'])==len(categories[r['category_name']]) and math.isclose(float(r['share_rate']),len(categories[r['category_name']])*100/denom) for r in shares))
rules=out('fp_growth')
for r in rules:
    a=set(map(int,r['antecedent_ids'].split(','))); b=set(map(int,r['consequent_ids'].split(',')))
    na=sum(a<=s for s in baskets.values()); nb=sum(b<=s for s in baskets.values()); nab=sum(a|b<=s for s in baskets.values()); n=len(baskets)
    check('association '+r['antecedent_ids']+' -> '+r['consequent_ids'],nab>=5 and all(math.isclose(float(r[k]),v,rel_tol=1e-8) for k,v in [('support',nab/n),('confidence',nab/na),('lift',nab*n/na/nb)]))
def itemcf(data):
    positive=defaultdict(set); seen=defaultdict(set); users=defaultdict(set)
    for r in data:
        u,c=r['user_id'],r['course_id']; seen[u].add(c)
        if r['is_finish']==1 or r['progress']>=60: positive[u].add(c); users[c].add(u)
    result={}
    for u,known in positive.items():
        scores={c:sum(len(users[c]&users[s])/math.sqrt(len(users[c])*len(users[s])) for s in known if c!=s) for c in users if c not in seen[u] and c in courses}
        top=sorted(((c,s) for c,s in scores.items() if s>0),key=lambda x:(-x[1],x[0]))[:5]
        lo=min((s for _,s in top),default=0); hi=max((s for _,s in top),default=0)
        for c,s in top: result[u,c] = 1 if hi==lo else (s-lo)/(hi-lo)
    return result
expected=itemcf(logs); actual=out('item_cf')
check('ItemCF exact candidate pairs independently recomputed',set(expected)=={(int(r['user_id']),int(r['course_id'])) for r in actual})
check('ItemCF normalized scores independently recomputed',all(abs(float(r['score'])-expected[int(r['user_id']),int(r['course_id'])])<=0.000051 for r in actual))
recommendations={}
for task in ('item_cf','als_recommend'):
    recs=out(task); pairs=[(int(r['user_id']),int(r['course_id'])) for r in recs]; counts=Counter(u for u,c in pairs)
    check(task+' no duplicate, learned, invalid course or >5 recommendations',len(set(pairs))==len(pairs) and all(c in courses and c not in baskets[u] and math.isfinite(float(r['score'])) for (u,c),r in zip(pairs,recs)) and max(counts.values(),default=0)<=5)
    recommendations[task]={'rows':len(recs),'users':len(counts),'metrics':out(task+'_metrics')}
dates=sorted(days); cutoff=dates[int(len(dates)*.8)]
train=[r for r in logs if r['study_date']<cutoff]; seen={(r['user_id'],r['course_id']) for r in train}; known={u for u,c in seen}
targets={(r['user_id'],r['course_id']) for r in logs if r['study_date']>=cutoff and (r['is_finish'] or r['progress']>=60) and (r['user_id'],r['course_id']) not in seen and r['user_id'] in known}
eligible={u for u,c in targets}; pop=Counter(c for u,c in seen); baseline=set()
for u in eligible:
    baseline.update((u,c) for c in sorted((c for c in pop if c in courses and (u,c) not in seen),key=lambda c:(-pop[c],c))[:5])
preds={p for p in itemcf(train) if p[0] in eligible}
metrics={r['metric']:r for r in out('item_cf_metrics')}
if len(eligible)>=3 and len(targets)>=5:
    vals={'recall_at_5':len(preds&targets)/len(targets),'precision_at_5':len(preds&targets)/(len(eligible)*5),'user_coverage':len({u for u,c in preds})/len(eligible),'popular_baseline_recall_at_5':len(baseline&targets)/len(targets)}
    vals['recall_gain_over_popular']=vals['recall_at_5']-vals['popular_baseline_recall_at_5']
    check('ItemCF holdout and popular baseline metrics independently recomputed',all(math.isclose(float(metrics[k]['value']),v) for k,v in vals.items()))
    alsmetrics={r['metric']:r for r in out('als_recommend_metrics')}
    reported_hits=int(re.search(r'hits=(\d+)',alsmetrics['recall_at_5']['detail']).group(1))
    check('ALS metric arithmetic and independently recomputed baseline',math.isclose(float(alsmetrics['recall_at_5']['value']),reported_hits/len(targets)) and math.isclose(float(alsmetrics['precision_at_5']['value']),reported_hits/(len(eligible)*5)) and math.isclose(float(alsmetrics['popular_baseline_recall_at_5']['value']),len(baseline&targets)/len(targets)))
text=out('text_classify'); check('text classification total questions conserved',sum(int(r['question_count']) for r in text)==len(questions))
byprovince=Counter(r['province'] for r in questions); got=Counter()
for r in text: got[r['province']]+=int(r['question_count'])
check('text classification province totals conserved',byprovince==got)
clusters=out('kmeans'); active=defaultdict(set); duration=Counter(); finished=defaultdict(set)
for r in logs:
    u=r['user_id']; active[u].add(r['study_date']); duration[u]+=r['study_time']
    if r['is_finish']: finished[u].add(r['course_id'])
check('cluster user counts conserved',sum(int(r['user_count']) for r in clusters)==len(baskets))
check('cluster weighted daily minutes conserved',math.isclose(sum(int(r['user_count'])*float(r['avg_study_time']) for r in clusters),sum(duration[u]/len(active[u]) for u in baskets)))
check('cluster weighted completion rate conserved',math.isclose(sum(int(r['user_count'])*float(r['avg_finish_rate']) for r in clusters),sum(len(finished[u])/len(baskets[u]) for u in baskets)))
question_counts=Counter(r['question'] for r in questions)
word_rows=out('word_freq')
check('word frequencies bounded by question count and sorted',all(3<=int(r['freq'])<=len(questions) for r in word_rows) and [int(r['freq']) for r in word_rows]==sorted([int(r['freq']) for r in word_rows],reverse=True))
regressions=0; course_max={}
for r in sorted(logs,key=lambda r:r['study_date']):
    pair=r['user_id'],r['course_id']; previous=course_max.get(pair,0)
    if r['progress']<previous: regressions+=1
    course_max[pair]=max(previous,r['progress'])
summary={'input':{'users':len(baskets),'logs':len(logs),'questions':len(questions),'unique_question_texts':len(question_counts),'human_labels':len(read(root/'in/question_labels.csv')),'courses':len(courses),'active_dates':len(days),'minutes':sum(minutes.values()),'progress_below_prior_max_rows':regressions},'checks':checks,'clusters':clusters,'cluster_metrics':out('kmeans_metrics'),'association_rules':len(rules),'holdout':{'cutoff':cutoff,'users':len(eligible),'targets':len(targets),'popular_hits':len(baseline&targets)},'recommendations':recommendations,'text_metrics':out('text_classify_metrics'),'task_status':read(root/'task-status.tsv','\t')}
(root/'independent-checks.json').write_text(json.dumps(summary,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps(summary,ensure_ascii=False,indent=2))
