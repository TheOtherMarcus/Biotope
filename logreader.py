
import json
import sys
import os.path
import time
import psycopg2
import properties

create_table = """create table {0} ({1}, unique({2}));"""
create_index = """create index {0}${1}_idx on {0} ({1});"""
create_view_cn = """CREATE VIEW {0}cn AS (SELECT {1}, t FROM (SELECT DISTINCT ON ({1}) {1}, o, max(t) as t FROM {0} GROUP BY o, {1} ORDER BY {1}, COUNT(o) DESC, o ASC) AS c WHERE o='t');"""
create_view_c1 = """CREATE VIEW {0}c1 AS (SELECT {1}, t FROM {0}cn ORDER BY t DESC LIMIT 1);"""
create_view_cnn = """CREATE VIEW {0}cnn AS (SELECT {1}, t FROM (SELECT DISTINCT ON ({1}) {1}, o, max(t) as t FROM {0} GROUP BY o, {1} ORDER BY {1}, COUNT(o) DESC, o ASC) AS c WHERE o='t');"""
create_view_cxy = """CREATE VIEW {0}{3} AS (SELECT DISTINCT ON ({2}) {1}, t FROM {0}cnn ORDER BY {2}, t DESC);"""
create_view_c11 = """CREATE VIEW {0}c11 AS (SELECT {1}, t from {0}c1n INTERSECT SELECT {1}, t from {0}cn1);"""

insert = """insert into {0} ({1}) values ({2});"""
delete = """delete from {0} where {1};"""

dbrelations = []
markprefix = 'mark';
revert = False;        

def logentry2sql(filename):
    global dbrelations
    result = []
    schema = []
    try:
        operations = json.loads(open(filename, "r").read())
    except:
        return (result, schema)
    # The file contains an array of operation.
    for operation in operations:
        # Only consider valid operations
        if operation.has_key('r'):
            r = operation['r']
            if operation.has_key('t') and operation.has_key('o') :
                o = operation['o'].replace("'", "''")
                t = operation['t'].replace("'", "''")
                if operation.has_key('a') and operation.has_key('b'):
                    a = operation['a'].replace("'", "''")
                    b = operation['b'].replace("'", "''")
                    result += [insert.format(r, 'a, b, o, t', "'{0}', '{1}', '{2}', '{3}'".format(a, b, o, t))]
                    if r not in dbrelations:
                        dbrelations += [r]
                        schema += [create_table.format(r, 'a uuid, b uuid, o bool, t timestamp', 'a, b, o, t')]
                        schema += [create_index.format(r, 'a')]
                        schema += [create_index.format(r, 'b')]
                        schema += [create_index.format(r, 'o')]
                        schema += [create_index.format(r, 't')]
                        schema += [create_view_cnn.format(r, 'a, b')]
                        schema += [create_view_cxy.format(r, 'a, b', 'a', 'cn1')]
                        schema += [create_view_cxy.format(r, 'a, b', 'b', 'c1n')]
                        schema += [create_view_c11.format(r, 'a, b')]
                elif operation.has_key('a') and operation.has_key('h'):
                    a = operation['a'].replace("'", "''")
                    h = operation['h'].replace("'", "''")
                    result += [insert.format(r, 'a, h, o, t', "'{0}', '{1}', '{2}', '{3}'".format(a, h, o, t))]
                    if r not in dbrelations:
                        dbrelations += [r]
                        schema += [create_table.format(r, 'a uuid, h char(64), o bool, t timestamp', 'a, h, o, t')]
                        schema += [create_index.format(r, 'a')]
                        schema += [create_index.format(r, 'h')]
                        schema += [create_index.format(r, 'o')]
                        schema += [create_index.format(r, 't')]
                        schema += [create_view_cnn.format(r, 'a, h')]
                        schema += [create_view_cxy.format(r, 'a, h', 'a', 'cn1')]
                        schema += [create_view_cxy.format(r, 'a, h', 'h', 'c1n')]
                        schema += [create_view_c11.format(r, 'a, h')]
                elif operation.has_key('h') and operation.has_key('i'):
                    h = operation['h'].replace("'", "''")
                    i = operation['i'].replace("'", "''")
                    result += [insert.format(r, 'h, i, o, t', "'{0}', '{1}', '{2}', '{3}'".format(h, i, o, t))]
                    if r not in dbrelations:
                        dbrelations += [r]
                        schema += [create_table.format(r, 'h char(64), i char(64), o bool, t timestamp', 'h, i, o, t')]
                        schema += [create_index.format(r, 'h')]
                        schema += [create_index.format(r, 'i')]
                        schema += [create_index.format(r, 'o')]
                        schema += [create_index.format(r, 't')]
                        schema += [create_view_cnn.format(r, 'h, i')]
                        schema += [create_view_cxy.format(r, 'h, i', 'h', 'cn1')]
                        schema += [create_view_cxy.format(r, 'h, i', 'i', 'c1n')]
                        schema += [create_view_c11.format(r, 'h, i')]
                elif operation.has_key('a'):
                    a = operation['a'].replace("'", "''")
                    result += [insert.format(r, 'a, o, t', "'{0}', '{1}', '{2}'".format(a, o, t))]
                    if r not in dbrelations:
                        dbrelations += [r]
                        schema += [create_table.format(r, 'a uuid, o bool, t timestamp', 'a, o, t')]
                        schema += [create_index.format(r, 'a')]
                        schema += [create_index.format(r, 'o')]
                        schema += [create_index.format(r, 't')]
                        schema += [create_view_cn.format(r, 'a')]
                        schema += [create_view_c1.format(r, 'a')]
                elif operation.has_key('h'):
                    h = operation['h'].replace("'", "''")
                    result += [insert.format(r, 'h, o, t', "'{0}', '{1}', '{2}'".format(h, o, t))]
                    if r not in dbrelations:
                        dbrelations += [r]
                        schema += [create_table.format(r, 'h char(64), o bool, t timestamp', 'h, o, t')]
                        schema += [create_index.format(r, 'h')]
                        schema += [create_index.format(r, 'o')]
                        schema += [create_index.format(r, 't')]
                        schema += [create_view_cn.format(r, 'h')]
                        schema += [create_view_c1.format(r, 'h')]
                else:
                    print ""
                    print "WARNING: Relation '{0}' ignored".format(r)
            elif r == 'text_value':
                h = operation['h'].replace("'", "''")
                v = operation['v'].replace("'", "''").encode('utf8')
                result += [insert.format(r, 'h, v', "'{0}', '{1}'".format(h, v))]
                if r not in dbrelations:
                    dbrelations += [r]
                    schema += [create_table.format(r, 'h char(64), v text', 'h, v')]
                    schema += [create_index.format(r, 'h')]
                    schema += [create_index.format(r, 'v')]
            elif r == 'date_value':
                h = operation['h'].replace("'", "''")
                v = operation['v'].replace("'", "''").encode('utf8')
                result += [insert.format(r, 'h, v', "'{0}', '{1}'".format(h, v))]
                if r not in dbrelations:
                    dbrelations += [r]
                    schema += [create_table.format(r, 'h char(64), v date', 'h, v')]
                    schema += [create_index.format(r, 'h')]
                    schema += [create_index.format(r, 'v')]
            elif r == 'timestamp_value':
                h = operation['h'].replace("'", "''")
                v = operation['v'].replace("'", "''").encode('utf8')
                result += [insert.format(r, 'h, v', "'{0}', '{1}'".format(h, v))]
                if r not in dbrelations:
                    dbrelations += [r]
                    schema += [create_table.format(r, 'h char(64), v timestamp', 'h, v')]
                    schema += [create_index.format(r, 'h')]
                    schema += [create_index.format(r, 'v')]
            elif r == 'numeric_value':
                h = operation['h'].replace("'", "''")
                v = operation['v'].replace("'", "''").encode('utf8')
                result += [insert.format(r, 'h, v', "'{0}', '{1}'".format(h, v))]
                if r not in dbrelations:
                    dbrelations += [r]
                    schema += [create_table.format(r, 'h char(64), v numeric', 'h, v')]
                    schema += [create_index.format(r, 'h')]
                    schema += [create_index.format(r, 'v')]
            else:
                print ""
                print "WARNING: Relation '{0}' ignored".format(r)
    return (result, schema)

def execute(conn, stmts):
    for stmt in stmts:
        cur = conn.cursor()
        try:
            print stmt
            cur.execute(stmt)
            conn.commit()
        except Exception, e:
            print ""
            print "EXCEPTION: {0}".format(e)
            conn.rollback();
        cur.close()

# Parse arguments
if sys.argv[1] == '-r':
    markprefix = 'revert';
    revert = True;
    sys.argv = sys.argv[1:]

        
# Connect to database
try:
    p = properties.read('servlets/web/WEB-INF/classes/biotope.properties')
    conn=psycopg2.connect("host='{0}' dbname='{1}' user='{2}' password='{3}'".format(p['db.servername'], p['db.databasename'], p['db.user'], p['db.password']))
except:
    print "I am unable to connect to the database."
    exit(1)

cur = conn.cursor()
try:
    cur.execute("""SELECT tablename from pg_tables where tableowner='{0}'""".format(p['db.user']))
except:
    print "Relation select failed"
    conn.close()
    exit(1)
for (relation,) in cur.fetchall():
    dbrelations = dbrelations + [relation]
cur.close()

print dbrelations

# Find the last mark
mark = os.popen("cd {0}; ls -t {1}* | head -1".format(sys.argv[1], markprefix)).read()
if len(mark) == 0:
    mark = markprefix + "1"
    starttime = 0
else:
    mark = mark[:-1]
    starttime = os.path.getmtime(os.path.join(sys.argv[1], mark))
print mark
markname = os.path.join(sys.argv[1], mark)
nextmarkname = os.path.join(sys.argv[1], markprefix + str(int(mark[len(markprefix):]) + 1))
os.system("touch " + nextmarkname)
print "touch " + nextmarkname
endtime = os.path.getmtime(nextmarkname)
os.system("rm " + markname)
print "rm " + markname

# Read existing log entries
for f in os.listdir(sys.argv[1]):
    filename = os.path.join(sys.argv[1], f)
    t = os.path.getmtime(filename)
    if os.path.isfile(filename) and t > starttime and t <= endtime:
        (rows, schema) = logentry2sql(filename)
        for row in schema:
            execute(conn, [row])
        execute(conn, rows)

conn.close()
