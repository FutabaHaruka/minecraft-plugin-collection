#!/usr/bin/env python3
import itertools, random
MAX_IV=31

def strict_target(a,b,allow_zero=True,zero_target=0,min_parent=1,max_parent=5,max_result=6,allow_max=False):
    if (a==0 or b==0) and not allow_zero: return None
    if a!=b: return None
    if a==0: return zero_target
    if a<min_parent or a>max_parent: return None
    if a>=max_result: return max_result if allow_max else None
    return min(max_result,a+1)

assert strict_target(0,0)==0
for v in range(1,6):
    assert strict_target(v,v)==v+1
for a in range(7):
    for b in range(7):
        if a!=b:
            assert strict_target(a,b) is None
assert strict_target(6,6) is None
assert strict_target(6,6,max_parent=6,allow_max=True)==6

def exact(first, second, egg, target, locked_index=-1, locked_value=-1, seed=0):
    rng=random.Random(seed)
    chosen=[False]*6
    count=0
    if locked_index>=0 and locked_value==31:
        chosen[locked_index]=True; count=1
    inherited=[i for i in range(6) if i!=locked_index and (first[i]==31 or second[i]==31)]
    rng.shuffle(inherited)
    for i in inherited:
        if count>=target: break
        chosen[i]=True; count+=1
    if count<target:
        candidates=[i for i in range(6) if not chosen[i] and i!=locked_index]
        rng.shuffle(candidates)
        candidates.sort(key=lambda i:max(first[i],second[i]), reverse=True)
        for i in candidates:
            if count>=target: break
            chosen[i]=True; count+=1
    out=list(egg)
    for i in range(6):
        if i==locked_index: out[i]=max(0,min(31,locked_value))
        elif chosen[i]: out[i]=31
        else:
            value=out[i]
            if value>=31:
                av=30 if first[i]>=31 else first[i]
                bv=30 if second[i]>=31 else second[i]
                value=max(av,bv)
            out[i]=max(0,min(30,value))
    return out

cases=0
for tier in range(0,6):
    target=0 if tier==0 else tier+1
    for left in itertools.combinations(range(6),tier):
      for right in itertools.combinations(range(6),tier):
        first=[31 if i in left else (i*7+3)%31 for i in range(6)]
        second=[31 if i in right else (i*11+5)%31 for i in range(6)]
        for egg in ([31]*6,[0,1,2,3,4,5],[30]*6):
          for seed in range(4):
            out=exact(first,second,egg,target,seed=seed)
            assert sum(v==31 for v in out)==target
            assert all(0<=v<=31 for v in out)
            cases+=1
        if tier>0:
          for idx in range(6):
            holder=first if first[idx]==31 else second
            if holder[idx]!=31: continue
            out=exact(first,second,[31]*6,target,idx,31,0)
            assert out[idx]==31
            assert sum(v==31 for v in out)==target
            cases+=1
print('STRICT_SYNTHESIS_OK',cases)
