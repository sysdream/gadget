from gadget import *
from time import sleep

g = GadgetWrapper('localhost',7777)
if g.connect():
    app = 'com.example.fino'
    g.attach(app)
    activity = g.filter_entrypoints('android.app.Activity')[0]
    #for field in activity.list_methods():
    #    print '- %s (%s)' % (field.get_name(), field.get_proto())
    print activity.mTitle
    g.disconnect()

