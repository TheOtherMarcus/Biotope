
LOGS="`find log -maxdepth 1 | grep /`"

for l in $LOGS; do
    echo $l
    python logreader.py $l
done;
