
import psycopg2
import properties

dbrelations = list()
entities = list()
types = list()
values = list()

# Connect to database
try:
    p = properties.read('biotope/web/WEB-INF/classes/biotope.properties')
    conn=psycopg2.connect("host='{0}' dbname='{1}' user='{2}' password='{3}'".format(p['db.servername'], p['db.databasename'], p['db.user'], p['db.password']))
except:
    print "I am unable to connect to the database."
    exit(1)

def query(sql):
    global conn
    cur = conn.cursor()
    try:
        cur.execute(sql)
    except:
        print "Relation select failed"
        print sql
        conn.close()
        exit(1)
    result = cur.fetchall()
    cur.close()
    conn.commit()
    return result

for (relation,) in query("""SELECT tablename from pg_tables where tableowner='{0}'""".format(p['db.user'])):
    dbrelations = dbrelations + [relation]

print dbrelations

f = open('schema.dot', 'wb')
f.write('graph schema {\n');

edges = list()

def edge(e):
    global f, edges
    if e not in edges:
        f.write(e)
        edges = edges + [e]
        
for r in dbrelations:
    if r[-2:] == '10':
        entities = entities + [r]
        f.write('{0} [label="{0}",shape="box",style=filled,fillcolor="lightgray"];\n'.format(r));
    if r[-2:] == '01':
        types = types + [r]
        f.write('{0} [label="{0}",shape="diamond",style=filled,fillcolor="yellow"];\n'.format(r));
    if r[-6:] == '_value':
        values = values + [r]
        f.write('{0} [label="{0}",style=filled,fillcolor="cyan"];\n'.format(r));
    else:
        f.write('{0} [label="{0}"];\n'.format(r));
        
for r in dbrelations:
    if r[-2:] == '20':
        for e1 in entities:
            for e2 in entities:
                q = """SELECT x0.a, x0.b from {0} x0, {1} x1, {2} x2 where x0.a = x1.a and x0.b = x2.a limit 1""".format(r, e1, e2)
                print q
                d = query(q)
                print len(d)
                if len(d) > 0:
                    edge('{0} -- {1} [label="a"];\n'.format(e1, r))
                    edge('{0} -- {1} [label="b"];\n'.format(r, e2))
    if r[-2:] == '02':
        for t1 in types:
            for t2 in types:
                q = """SELECT x0.h, x0.i from {0} x0, {1} x1, {2} x2 where x0.h = x1.h and x0.i = x2.h limit 1""".format(r, t1, t2)
                print q
                d = query(q)
                print len(d)
                if len(d) > 0:
                    edge('{0} -- {1} [label="h"];\n'.format(t1, r))
                    edge('{0} -- {1} [label="i"];\n'.format(r, t2))
                    
    if r[-2:] == '11':
        for e1 in entities:
            for t2 in types:
                q = """SELECT x0.a, x0.h from {0} x0, {1} x1, {2} x2 where x0.a = x1.a and x0.h = x2.h limit 1""".format(r, e1, t2)
                print q
                d = query(q)
                print len(d)
                if len(d) > 0:
                    edge('{0} -- {1} [label="a"];\n'.format(e1, r))
                    edge('{0} -- {1} [label="h"];\n'.format(r, t2))

    if r[-6:] == '_value':
        for t1 in types:
            q = """SELECT x0.h from {0} x0, {1} x1 where x0.h = x1.h limit 1""".format(r, t1)
            print q
            d = query(q)
            print len(d)
            if len(d) > 0:
                edge('{0} -- {1} [label="h"];\n'.format(t1, r))

f.write('}\n')
f.close()
