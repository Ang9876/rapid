import re
import os
import subprocess
path = "tests/tests5"
traceFile = "lufact.std"
eventFile = "events"
class_path = "$CLASSPATH:" + os.path.join(os.getcwd(), 'bin') + ":" + os.path.join(os.getcwd(), 'lib', '*') + ":" + os.path.join(os.getcwd(), 'lib', 'jgrapht', '*')

e1e2path = open(os.path.join(path, traceFile[:-4] + "e1e2"), "r")
for e1e2 in e1e2path.readlines():
    (e1, e2) = e1e2.split()
    f1 = open(os.path.join(path, eventFile), 'w')
    f1.write(e1 + "\n" + e2 + "\n")
    f1.close()
    print(e1, e2, end=" ", flush=True)
    cmd = ['java', '-cp', class_path, 'GrainSim', '-f', 'std', '-p', os.path.join(path, traceFile), '-m', os.path.join(path, eventFile)]
    subprocess.run(cmd, stderr=subprocess.STDOUT)
    print()