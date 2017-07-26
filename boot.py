
import sys
import uuid
from logwriter import set_archive, set_partition, bin_archive, string_digest, log, relate, erelate, trelate, entrelate, valrelate_no_data, relate_no_data, valrelate, vrelate

set_archive(sys.argv[1])
set_partition(sys.argv[1] + '.archive')

symbol = sys.argv[2]
filename = sys.argv[3]
data = open(filename).read()

(digest, path) = bin_archive(data)
url = "/" + path
urldigest = string_digest(url)

mimetype = 'text/plain'
if filename.endswith('_png'):
    mimetype = 'image/png'
elif filename.endswith('_gif'):
    mimetype = 'image/gif'

log("[" + ','.join(valrelate_no_data(mimetype, digest)) + "]")
log("[" + ','.join(valrelate('text/url', urldigest, url)) + "]")
log("[" + ','.join(vrelate('location', digest, urldigest)) + "]")

set_partition(sys.argv[1])
macro_uuid = str(uuid.uuid4())
log("[" +
    ','.join(entrelate('macro', macro_uuid)) + ',' +
    ','.join(relate('name', macro_uuid, 'text/plain', symbol)) + ',' +
    ','.join(relate_no_data('data', macro_uuid, digest)) + 
    "]")
