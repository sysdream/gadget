#!/usr/bin/python
import sys
from gadget import *
from time import sleep

g = GadgetWrapper('localhost',7777)
if g.connect():
    app = 'com.example.fino'
    g.attach(app)
    activity = g.filter_entrypoints('android.app.Activity')[0]
    hello = activity.mWindow.mContentParent.getChildAt(0).getChildAt(0)
    g.disconnect()

