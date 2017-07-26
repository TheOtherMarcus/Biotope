
def read(filename):
    p = {}
    with open(filename, 'r') as f:
        for line in f:
            line = line.rstrip()            
            if "=" not in line: continue
            if line.startswith("#"): continue
            k, v = line.split("=", 1)
            p[k] = v
    return p
