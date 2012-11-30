#!/usr/bin/python
import sys
from gadget import *
from time import sleep

g = GadgetWrapper('localhost',7777)
if g.connect():
    app = 'com.example.fino'
    g.attach(app)
    activity = g.filter_entrypoints('android.app.Activity')[0]
    activity.setTitle("Hello")
    print activity.mTitle
    g.disconnect()

