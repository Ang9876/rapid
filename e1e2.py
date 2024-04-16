import re
import os

path = "tests/tests5"
traceFile = "lufact.std"

def parse(event):
    (thread, op, _) = event.split("|")
    thread = int(thread[1:])
    x = re.match(r'(.*)\((.*)\)', op)
    op = x.group(1)
    var = x.group(2)
    return (thread, op, var)

def conflict(e1, e2):
    return (e1[0] != e2[0]) and (e1[2] == e2[2]) and (e1[1] == 'w' or e2[1] == 'w')

e2sets = list(map(lambda event: int(event), open(os.path.join(path, traceFile[:-4] + "e2set"), 'r').readlines()))
trace = open(os.path.join(path, traceFile), 'r').readlines()
candidateFile = open(os.path.join(path, traceFile[:-4] + "e1e2"), 'w')
for e2 in e2sets:
    event2 = parse(trace[e2 - 1])
    for e1 in range(e2 - 1, -1, -1):
        event1 = parse(trace[e1])
        if conflict(event1, event2):
            candidateFile.write(str(e1 + 1) + " " + str(e2) + "\n")
            break
candidateFile.close()