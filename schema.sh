#!/bin/sh

python schema.py
dot -Tpng -osrc/biotope/schema_png schema.dot 
