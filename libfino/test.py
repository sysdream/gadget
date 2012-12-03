#!/usr/bin/python
import sys
from gadget import *
from time import sleep

g = GadgetWrapper('localhost',7777)
if g.connect():
    app = 'com.example.fino'
    g.attach(app)
    activity = g.filter_entrypoints('android.app.Activity')[0]
    #activity.setTitle("Hello")
    #print activity.mTitle
    #activity.finish()
    r = g.newInstance('com.example.fino.R$id',[])
    g.sync_entrypoints()
    hello = int(str(g.get_entrypoint(r).test))
    hey = activity.findViewById(hello)
    hey.setText("Oops")

    #print g.get_classes(g.get_entrypoint(r))
    g.disconnect()

