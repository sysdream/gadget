import socket
import json
import struct
from types import *

"""
Helpers
"""

def trim(s):
    return s.rstrip().lstrip()

"""
Exceptions
"""

class GadgetNotAttached(Exception):
    """
    This exception is thrown when GadgetWrapper is not attached to a
    running application.
    """
    def __init__(self):
        Exception.__init__(self)

class InvocationError(Exception):
    """
    Remote method invocation error
    """
    def __init__(self):
        Exception.__init__(self)

"""
Remote Procedure Call
"""

class RpcRequest:
    """
    Fino's RPC request
    """
    def __init__(self, package, method, *args):
        """
        @param package target application package
        @param method target method name
        @args arguments
        """
        self.package = package
        self.method = method
        self.params = args

    def toJson(self):
        """
        Wrap the RPC request to JSON
        """
        return json.dumps([self.package, self.method]+[i for i in self.params])

    def __str__(self):
        """
        Return the raw request
        """
        request = self.toJson()
        return struct.pack('>I',len(request))+request

class EntryPoint(object):
    """
    Entrypoint model

    This class handles every entrypoint information and provide
    an easy way to interact with it. Methods and fields associated
    with an entrypoint are accessible directly as a classic python
    object:

    <code>MyEntrypoint.someMethod(3,2)</code>
    <code>print MyEntrypoint.mTitle</code>
    """
    def __init__(self, instance, clazz, index, path=[]):
        self._instance_name = instance
        self._clazz_name = clazz
        self._type = None
        self._index = index
        self._path = path
        self._gadget = Gadget.get_inst()
        self._fields = None
        self._methods = None

    def list_fields(self):
        """
        List fields.

        This method uses a cache to avoid getting all the fields
        every time this method is called.

        @return array of Field instances
        """
        if self._fields is None:
            self._fields = self._gadget.get_fields(self)
        return self._fields

    def list_methods(self):
        """
        List methods

        This method uses a cache to avoid getting all the fields
        every time this method is called.

        @return array of Method instances
        """
        if self._methods is None:
            self._methods = self._gadget.get_methods(self)
        return self._methods

    def get_gadget(self):
        return self._gadget

    def get_name(self):
        """
        Get entrypoint name
        """
        return self._instance_name

    def get_path(self):
        """
        Get entrypoint path
        """
        return self._path

    def get_class(self):
        """
        Get entrypoint class
        """
        return self._clazz_name

    def get_type(self):
        """
        Get enttypoint class name
        """
        if self._type is None:
            self._type = self._gadget.get_type(self)
        return self._type

    def get_root_index(self):
        """
        Get entrypoint index
        """
        return self._index

    def get_index(self):
        return self._path[-1]

    def __eq__(self, other):
        return ((self._instance_name == other.get_name()) and (self._clazz_name == other.get_class()))

    def __setattr__(self, attr, value):
        """
        Set field value
        """
        if attr not in ['_fields','_methods', '_gadget', '_path', '_index', '_clazz_name', '_instance_name', '_type']:
            self.list_fields()
            for field in self._fields:
                if attr == field.get_name():
                    field.set_value(value)
                    return True
            return False
        else:
            return object.__setattr__(self, attr, value)

    def __getattr__(self, attr):
        """
        Retrieve method or field

        This is where the 'magic' occurs. This method inspects all the fields
        and methods associated with the current type of entrypoint and check
        if the requested attribute matches. If it matches some methods with
        various prototypes, a VirtualMethod instance is returned that will
        select the correct version at call-time.

        @param attr attribute (Field or Method name)
        @return Either a VirtualMethod instance or field value
        """
        self.list_fields()
        for field in self._fields:
            if attr == field.get_name():
                return Field(
                    field.get_name(),
                    field.get_class(),
                    self._index,
                    self._path+[field.get_index()]
                )

        self.list_methods()
        for method in self._methods:
            if attr == method.get_name():
                return VirtualMethod(self, attr)
        raise AttributeError()

    @staticmethod
    def fromString(entrypoint, index, path):
        """
        Parse Entrypoint from string (as returned by Fino's injected service)
        """
        raw = entrypoint.split(':')
        return EntryPoint(trim(raw[0]), trim(raw[1]), index, path)

class VirtualMethod:
    """
    Virtual method

    This class implements on-the-fly method selection in order to manage
    method polymorphism. 
    """

    def __init__(self, ep, method):
        self._ep = ep
        self._method = method
        self._gadget = Gadget.get_inst()

    def __call__(self, *args):
        """
        Perform a call by name
        """
        result = self._gadget.invokeByName(self._ep, self._method, *args)
        if result == -2:
            raise InvocationError()
        else:
            self._gadget.sync_entrypoints()
            return self._gadget.get_entrypoint(result)


class Method:
    """
    Method

    This class handles every operation related to an object's methods.
    """

    def __init__(self, name, proto, ep, index, path=[]):
        """
        @param name string Method name
        @param proto string Method's prototype
        @param ep EntryPoint Parent EntryPoint instance
        @param index int Method's index
        @param path int[] Method path (from EntryPoint)
        @param gadget Gadget Wrapper instance
        """
        self._name = name
        self._proto = proto
        self._ep = ep
        self._index = index
        self._path = path
        self._gadget = Gadget.get_inst()

    def get_name(self):
        """
        Get method's name
        """
        return self._name

    def get_proto(self):
        """
        Get method's prototype
        """
        return self._proto

    def get_index(self):
        """
        Get method's index
        """
        return self._index

    def get_path(self):
        """
        Get method's path
        """
        return self._path

    def get_param_types(self):
        """
        Get method's parameters types
        """
        return self._gadget.get_method_params(
            self._ep,
            self
        )

    def __call__(self, *args):
        """
        Invoke method

        @param args call args
        @return call result
        """
        return self._gadget.invoke(
            self._ep,
            self,
            *args
        )

    @staticmethod
    def fromString(method, ep, index):
        """
        Instanciate a method from its string representation

        @param ep EntryPoint parent EntryPoint
        @param index int Method's index
        @param gadget GadgetWrapper instance of GadgetWrapper
        """
        raw = method.split(':')
        return Method(trim(raw[0]), trim(raw[1]), ep, index, [])

class Field(EntryPoint):
    """
    Field

    This class inherits from EntryPoint and only add a get_value() method.
    """
    def __init__(self, name, clazz, index, path=[]):
        EntryPoint.__init__(self, name, clazz, index, path=path)

    def __repr__(self):
        return self.get_value()

    def get_value(self):
        """
        Get field's value
        """
        if self.get_type() == 'java.lang.Integer':
            return int(self.get_gadget().get_value(self))
        elif self.get_type() == 'java.lang.String':
            return str(self.get_gadget().get_value(self))
        elif self.get_type() == 'java.lang.CharSequence':
            return str(self.get_gadget().get_value(self))
        else:
            return self.get_gadget().get_value(self)

    def set_value(self, value):
        """
        Set field's value
        """
        return self.get_gadget().set_value(self, value)

    @staticmethod
    def fromString(field, index, path):
        raw = field.split(':')
        return Field(trim(raw[0]), trim(raw[1]), index, path)

class RpcResponse:
    """
    Fino's RPC response
    """

    def __init__(self, result=False, obj=None):
        self._result = result
        self._obj = obj

    def is_success(self):
        """
        Check if the remote call was successful
        """
        return self._result

    def get_response(self):
        """
        Get server response
        """
        return self._obj

    @staticmethod
    def fromJson(raw_json):
        """
        Wrap the response inside an instance of this class
        """
        _obj = json.loads(raw_json)
        if 'response' not in _obj:
            _obj['response'] = None
        return RpcResponse(_obj['success'], _obj['response'])


class Class:
    """
    Expose remote class
    """
    def __init__(self, clazz):
        self._gadget = Gadget.get_inst()
        self._class_name = clazz

    def new(self, *args):
        return self._gadget.newInstance(self._class_name, args)

    def get_name(self):
        return self._class_name

class Gadget:
    """
    This is a gadget connector that wraps the service
    operations.

    This class exposes the same interface as the remote service
    with some magic added.
    """

    instance = None

    @staticmethod
    def get_inst(server='127.0.0.1', port=7777):
        if Gadget.instance is None:
            Gadget.instance = Gadget(server, port)
        return Gadget.instance


    def __init__(self, server='127.0.0.1', port=7777):
        self.server = server
        self.port = port
        self.sock = None
        self.target = None
        self.ep_stack = []
        self._is_attached = False

    def connect(self):
        """
        Connect to the remote service

        @return boolean True on success, False otherwise
        """
        try:
            self.sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            self.sock.connect((self.server, self.port))
            return True
        except IOError,e:
            return False

    def disconnect(self):
        """
        Disconnect the connector
        """
        if self.sock is not None:
            self.sock.close()

    def is_attached(self):
        """
        Check if we are attached to an existing app
        """
        return self._is_attached

    def ensure_attached(self):
        """
        Make sure we are currently attached to an application. If this is not the case,
        raises a GadgetNotAttached exception.

        @raise GadgetNotAttached
        """
        if not self._is_attached:
            raise GadgetNotAttached()
        else:
            return True

    def _wait_response(self):
        """
        Wait for the service to send back a response
        """
        try:
            _size = self.sock.recv(4)
            if len(_size)<4:
                return None
            size = struct.unpack('>I',_size)[0]
            data = ''
            while len(data) < size:
                bulk = self.sock.recv(size - len(data))
                if len(bulk) == 0:
                    return None
                data += bulk
            return RpcResponse.fromJson(data)
        except IOError,e:
            return None
        except socket.error, e:
            return None

    def _send_request(self, request):
        """
        Send a request to the remote server
        """
        self.sock.send(str(request))
        return self._wait_response()

    def select_app(self, app):
        """
        Select a given application (package)
        """
        self.target = app

    def list_apps(self):
        """
        Retrieve the target applications implementing our inspection service.

        @retrun array of package names
        """
        response = self._send_request(RpcRequest('','listApps'))
        if response is not None :
            if response.is_success():
                return response.get_response()
        return None

    def attach(self, app):
        """
        Attach gadget to an existing application.

        If the target application is not launched, then launch it
        and attach.

        @param app target application (package name)
        @return boolean True on success, False otherwise
        """
        response = self._send_request(RpcRequest(app, 'connectApp'))
        if response is not None:
            if response.is_success():
                self.select_app(app)
                self._is_attached = True
                return True
        self._is_attached = False
        return False

    def sync_entrypoints(self):
        """
        Synchronize entrypoints
        """
        self.get_entrypoints()

    def get_entrypoint(self, index):
        """
        Get an entrypoint

        @param index int EntryPoint index
        @return EntryPoint instance of EntryPoint
        """
        return self.ep_stack[index]

    def get_entrypoints(self):
        """
        Retrieve remote service entrypoints

        @remote list of EntryPoint
        """
        self.ensure_attached()
        response = self._send_request(RpcRequest(self.target, 'getEntryPoints'))
        if response is not None:
            if response.is_success():
                self.ep_stack = [EntryPoint.fromString(ep, index, []) for index,ep in enumerate(response.get_response())]
                return self.ep_stack
        return None

    def push_str(self, s):
        self.ensure_attached()
        response = self._send_request(RpcRequest(self.target, 'pushString', s))
        if response is not None:
            if response.is_success():
                self.sync_entrypoints()
                return self.get_entrypoint(int(response.get_response()))
        return None

    def push_int(self, i):
        self.ensure_attached()
        response = self._send_request(RpcRequest(self.target, 'pushInt', i))
        if response is not None:
            if response.is_success():
                self.sync_entrypoints()
                return self.get_entrypoint(int(response.get_response()))
        return None

    def push_bool(self, b):
        self.ensure_attached()
        response = self._send_request(RpcRequest(self.target, 'pushBoolean', b))
        if response is not None:
            if response.is_success():
                self.sync_entrypoints()
                return self.get_entrypoint(int(response.get_response()))
        return None

    def push_ep(self, ep):
        self.ensure_attached()
        response = self._send_request(RpcRequest(self.target, 'push', ep.get_index(), ep.get_path()))
        if response is not None:
            if response.is_success():
                self.sync_entrypoints()
                return self.get_entrypoint(int(response.get_response()))
        return None

    def push(self, obj):
        if type(obj) is IntType:
            return self.push_int(obj)
        elif type(obj) is StringType:
            return self.push_str(obj)
        elif type(obj) is BooleanType:
            return self.push_bool(obj)
        elif isinstance(obj, EntryPoint):
            return self.push_ep(obj)
        else:
            return None

    def filter_entrypoints(self, clazz):
        """
        Return entrypoints of a given type
        """
        self.ensure_attached()
        self.sync_entrypoints()
        response = self._send_request(RpcRequest(self.target, 'filterEntryPoints', clazz))
        if response is not None:
            if response.is_success():
                return [self.ep_stack[i] for i in response.get_response()]
        return None

    def get_fields(self, ep):
        """
        Retrieve fields of a given EntryPoint accessible from path.
        """
        self.ensure_attached()
        response = self._send_request(RpcRequest(self.target, 'getFields', ep.get_root_index(), ep.get_path()))
        if response is not None:
            if response.is_success():
                return [Field.fromString(field, ep.get_root_index(), ep.get_path()+[index]) for index,field in enumerate(response.get_response())]
        return None

    def get_methods(self, ep):
        """
        Retrieve methods of a given EP accessible from path
        """
        self.ensure_attached()
        response = self._send_request(RpcRequest(self.target, 'getMethods', ep.get_root_index(), ep.get_path()))
        if response is not None:
            if response.is_success():
                return [Method.fromString(method, ep, index) for index,method in enumerate(response.get_response())]
        return None

    def get_classes(self, ep):
        """
        Retrieve declared classes of a given EP accessible from path
        """
        self.ensure_attached()
        response = self._send_request(RpcRequest(self.target, 'getClasses', ep.get_root_index(), ep.get_path()))
        if response is not None:
            if response.is_success():
                return response.get_response()
        return None

    def get_type(self, ep):
        """
        Retrieve entrypoint's class name
        """
        self.ensure_attached()
        response = self._send_request(RpcRequest(self.target, 'getType',ep.get_root_index(), ep.get_path()))
        if response is not None:
            if response.is_success():
                return response.get_response()
        return None

    def get_method_params(self, ep, method):
        """
        Get method params
        """
        self.ensure_attached()
        response = self._send_request(RpcRequest(self.target,'getMethodParams', ep.get_root_index(), ep.get_path(), method.get_index(), []))
        if response is not None:
            if response.is_success():
                return response.get_response()
        return None

    def set_value(self, ep, value):
        """
        Set a field's value
        
        @param ep Field the field
        @param value int EntryPoint to use as a value
        @return bool True on success, False otherwise
        """
        self.ensure_attached()
        val_ep = self.push(value)
        #print 'value ep: ',val_ep.get_root_index()
        response = self._send_request(RpcRequest(self.target, 'setValue', ep.get_root_index(), ep.get_path(), val_ep.get_root_index()))
        if response is not None:
            if response.is_success():
                return response.get_response()
        return None

    def get_value(self, ep):
        """
        Get a field's value

        @param ep Field the field
        @return the value
        """
        self.ensure_attached()
        response = self._send_request(RpcRequest(self.target, 'getValue', ep.get_root_index(), ep.get_path()))
        if response is not None:
            if response.is_success():
                return response.get_response()
        return None

    def invokeByName(self, ep, method, *args):
        """
        Remote method invocation by name
        """
        self.ensure_attached()
        eps = []
        for arg in args:
            eps.append(self.push(arg).get_root_index())
        response = self._send_request(RpcRequest(self.target, "invokeMethodByName", ep.get_root_index(), ep.get_path(), method, eps))
        if response is not None:
            if response.is_success():
                return response.get_response()
        return None

    def invoke(self, ep, method, *args):
        """
        Remote method invocation

        @param ep EntryPoint Root object
        @param method Method method to call
        @param args list List of EntryPoint indexes
        @return call result
        """
        self.ensure_attached()
        response = self._send_request(RpcRequest(self.target, 'invokeMethod', ep.get_root_index(), ep.get_path(), method.get_index(), args))
        if response is not None:
            if response.is_success():
                return response.get_response()
        return None

    def newInstance(self, clazz, params):
        """
        Create new instance

        @param clazz Class name
        @return new instance
        """
        self.ensure_attached()
        eps = []
        for param in params:
            eps.append(self.push(param).get_root_index())
        response = self._send_request(RpcRequest(self.target, 'newInstance', clazz, eps))
        if response is not None:
            if response.is_success():
                r = int(response.get_response())
                if r>=0:
                    self.sync_entrypoints()
                    return self.get_entrypoint(r)
        return None
