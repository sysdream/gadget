#!/usr/bin/python
import sys
from gadget2 import *
from time import sleep

g = Gadget.get_inst('localhost',7777)
if g.connect():
    app = 'com.example.fino'
    g.attach(app)
    activity = g.filter_entrypoints('android.app.Activity')[0]
    Rid = Class('com.example.fino.R$id').new()
    g.disconnect()

