#!/usr/bin/env python3
import random

def build(a,b,rng):
    return [min(x,y)+rng.randrange(abs(x-y)+1) for x,y in zip(a,b)]

a=[15,25,8,20,6,21]
b=[26,11,4,24,8,16]
for seed in range(10000):
    out=build(a,b,random.Random(seed))
    for i,v in enumerate(out):
        assert min(a[i],b[i]) <= v <= max(a[i],b[i])
# Equal parents must be inherited exactly.
assert build([0,1,2,3,30,31],[0,1,2,3,30,31],random.Random(1)) == [0,1,2,3,30,31]
# Confirm both inclusive endpoints are reachable for every non-equal sample stat.
seen=[set() for _ in range(6)]
for seed in range(100000):
    out=build(a,b,random.Random(seed))
    for i,v in enumerate(out): seen[i].add(v)
for i in range(6):
    assert min(a[i],b[i]) in seen[i]
    assert max(a[i],b[i]) in seen[i]
print('PARENT_RANGE_OK', [f'{min(x,y)}..{max(x,y)}' for x,y in zip(a,b)])
