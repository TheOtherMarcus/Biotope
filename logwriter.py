import os
import hashlib
import uuid
import json
import datetime
import pytz

partition='default'
archive='default'

def set_partition(p):
    global partition
    partition = p
    try:
        os.makedirs('log/' + p)
    except:
        pass
        
def set_archive(a):
    global archive
    archive = a
    try:
        os.makedirs('archive/' + a)
    except:
        pass
        
def log(e):
    global partition
#    print e
    data = json.dumps(json.loads(e), indent=2, separators=(',', ': ')).encode("utf-8")
    sha256 = hashlib.sha256()
    sha256.update(data)
    name = sha256.hexdigest()
    f = open("log/" + partition + "/" + name, "w")
    f.write(data)
    f.close()

def string_digest(string):
    return bin_digest(string.encode('utf-8'))
    
def bin_digest(data):
    sha256 = hashlib.sha256()
    sha256.update(data)
    return sha256.hexdigest()    
    
def bin_archive(data):
    global archive
    digest = bin_digest(data)
    d = "archive/" + archive
    return bin_archiver(data, digest, d, digest)
    
def bin_archiver(data, digest, d, rem):
    if not os.path.isdir(d):
        os.makedirs(d)
    if rem in os.listdir(d):
        # File already exists
        return (digest, d + '/' + rem)
    elif len(os.listdir(d)) < 16:
        f = open(d + '/' + rem, "w")
        f.write(data)
        f.close()
        return (digest, d + '/' + rem)
    else:
        return bin_archiver(data, digest, d + "/" + rem[0], rem[1:])

def entrelate(entity, uuid):
    return [u'{{"t":"{2}","o":"t","r":"{0}10","a":"{1}"}}'.format(entity, uuid, datetime.datetime.now(pytz.utc).isoformat())]
    
def valrelate(mimetype, digest, data):
    if mimetype == 'text/date':
        relation = 'date_value'
        log(u'[{{"r":"{0}","h":"{1}","v":"{2}"}}]'.format('text_value', digest, data))
    elif mimetype == 'text/timestamp':
        relation = 'timestamp_value'
        log(u'[{{"r":"{0}","h":"{1}","v":"{2}"}}]'.format('text_value', digest, data))
    elif mimetype == 'text/numeric':
        relation = 'numeric_value'
        log(u'[{{"r":"{0}","h":"{1}","v":"{2}"}}]'.format('text_value', digest, data))
    else:
        relation = 'text_value'
    log(u'[{{"r":"{0}","h":"{1}","v":"{2}"}}]'.format(relation, digest, data))
    return valrelate_no_data(mimetype, digest)
    
def valrelate_no_data(mimetype, digest):
    return [u'{{"t":"{2}","o":"t","r":"{0}01","h":"{1}"}}'.format(mimetype.replace('/', '_').replace('+', '_').replace('-', '_'), digest, datetime.datetime.now(pytz.utc).isoformat())]
    
def relate(relation, uuid, mimetype, data):
    digest = string_digest(data)
    return valrelate(mimetype, digest, data) + relate_no_data(relation, uuid, digest)

def relate_no_data(relation, uuid, digest):
    return [u'{{"t":"{3}","o":"t","r":"{0}11","a":"{1}","h":"{2}"}}'.format(relation, uuid, digest, datetime.datetime.now(pytz.utc).isoformat())]

def erelate(relation, uuid, uuid2):
    return [u'{{"t":"{3}","o":"t","r":"{0}20","a":"{1}","b":"{2}"}}'.format(relation, uuid, uuid2, datetime.datetime.now(pytz.utc).isoformat())]

def trelate(relation, uuid, mimetype, begin, end):
    begindigest = string_digest(begin)
    enddigest = string_digest(end)
    return valrelate(mimetype, begindigest, begin) + valrelate(mimetype, enddigest, end) + [u'{{"t":"{4}","o":"t","r":"{0}12","a":"{1}","h":"{2}","i":"{3}"}}'.format(relation, uuid, begindigest, enddigest, datetime.datetime.now(pytz.utc).isoformat())]

def vrelate(relation, digest1, digest2):
    return [u'{{"t":"{3}","o":"t","r":"{0}02","h":"{1}","i":"{2}"}}'.format(relation, digest1, digest2, datetime.datetime.now(pytz.utc).isoformat())] 

def vdissociate(relation, digest1, digest2):
    return [u'{{"t":"{3}","o":"f","r":"{0}02","h":"{1}","i":"{2}"}}'.format(relation, digest1, digest2, datetime.datetime.now(pytz.utc).isoformat())] 

