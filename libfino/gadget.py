import socket
import json
import struct

"""
Helpers
"""

def trim(s):
    return s.rstrip().lstrip()

"""
Java types hierarchy helpers
"""

JAVA_TYPES_EQ = {
    u'java.lang.CharSequence':[
        u'java.lang.String'
    ]
}

def is_equivalent(t1, t2):
    if t1 == t2:
        return True
    else:
        if t1 in JAVA_TYPES_EQ:
            return (t2 in JAVA_TYPES_EQ[t1])
        elif t2 in JAVA_TYPES_EQ:
            return (t1 in JAVA_TYPES_EQ[t2])
        else:
            return False

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

class EntryPoint:
    """
    Entrypoint model

    This class handles every entrypoint information and provide
    an easy way to interact with it. Methods and fields associated
    with an entrypoint are accessible directly as a classic python
    object:

    <code>MyEntrypoint.someMethod(3,2)</code>
    <code>print MyEntrypoint.mTitle</code>
    """
    def __init__(self, instance, clazz, index, path=[], gadget=None):
        self._instance_name = instance
        self._clazz_name = clazz
        self._index = index
        self._path = path
        self._gadget = gadget
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

    def get_index(self):
        """
        Get entrypoint index
        """
        return self._index

    def __eq__(self, other):
        return ((self._instance_name == other.get_name()) and (self._clazz_name == other.get_class()))

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
                    self._path+[field.get_index()],
                    self._gadget
                ).get_value()
        self.list_methods()
        candidates = []
        for method in self._methods:
            if attr == method.get_name():
                candidates.append(method)
        if len(candidates)>0:
                return VirtualMethod(self, candidates, self._gadget)
        raise AttributeError()

    @staticmethod
    def fromString(entrypoint, index, gadget):
        """
        Parse Entrypoint from string (as returned by Fino's injected service)
        """
        raw = entrypoint.split(':')
        return EntryPoint(trim(raw[0]), trim(raw[1]), index, [], gadget)

class VirtualMethod:
    """
    Virtual method

    This class implements on-the-fly method selection in order to manage
    method polymorphism. 
    """

    def __init__(self, ep, methods, gadget):
        self._ep = ep
        self._methods = methods
        self._gadget = gadget

    def __call__(self, *args):
        """
        Select on-the-fly one method from a set of methods

        @raise AttributeError if the required attribute is not found
        @return Call result
        """
        types = []
        for method in self._methods:
            types.append(method.get_param_types())

        # compute param types for target method
        arg_types = []
        for ep_index in args:
            entrypoint = self._gadget.get_entrypoint(ep_index)
            arg_types.append(entrypoint.get_class())
        
        for index,candidate in enumerate(types):
            if len(candidate)==len(arg_types):
                i = 0
                for t1 in candidate:
                    for t2 in arg_types:
                        if not is_equivalent(t1,t2):
                            break
                        i += 1
                if i==len(candidate):
                    return self._methods[index](*args)
        raise InvocationError()


class Method:
    """
    Method

    This class handles every operation related to an object's methods.
    """

    def __init__(self, name, proto, ep, index, path=[], gadget=None):
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
        self._gadget = gadget

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
    def fromString(method, ep, index, gadget):
        """
        Instanciate a method from its string representation

        @param ep EntryPoint parent EntryPoint
        @param index int Method's index
        @param gadget GadgetWrapper instance of GadgetWrapper
        """
        raw = method.split(':')
        return Method(trim(raw[0]), trim(raw[1]), ep, index, [], gadget)

class Field(EntryPoint):
    """
    Field

    This class inherits from EntryPoint and only add a get_value() method.
    """
    def __init__(self, name, clazz, index, path=[], gadget=None):
        EntryPoint.__init__(self, name, clazz, index, path=path, gadget=gadget)
        self._gadget = gadget

    def get_value(self):
        """
        Get field's value
        """
        return self._gadget.get_value(self)

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
        return RpcResponse(_obj['success'], _obj['response'])

class GadgetWrapper:
    """
    This is a gadget connector that wraps the service
    operation.

    This class exposes the same interface as the remote service
    with some magic added.
    """

    def __init__(self, server='128.0.0.1', port=7777):
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
                self.ep_stack = [EntryPoint.fromString(ep,index, self) for index,ep in enumerate(response.get_response())]
                return self.ep_stack
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
        response = self._send_request(RpcRequest(self.target, 'getFields', ep.get_index(), ep.get_path()))
        if response is not None:
            if response.is_success():
                return [Field.fromString(field, index, self) for index,field in enumerate(response.get_response())]
        return None

    def get_methods(self, ep):
        """
        Retrieve methods of a given EP accessible from path
        """
        self.ensure_attached()
        response = self._send_request(RpcRequest(self.target, 'getMethods', ep.get_index(), ep.get_path()))
        if response is not None:
            if response.is_success():
                return [Method.fromString(method, ep, index, self) for index,method in enumerate(response.get_response())]
        return None

    def get_method_params(self, ep, method):
        """
        Get method params
        """
        self.ensure_attached()
        response = self._send_request(RpcRequest(self.target,'getMethodParams', ep.get_index(), ep.get_path(), method.get_index(), []))
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
        response = self._send_request(RpcRequest(self.target, 'getValue', ep.get_index(), ep.get_path()))
        if response is not None:
            if response.is_success():
                return response.get_response()
        return None

    def set_value(self, ep, value):
        """
        Set a field's value

        @param ep Field the field
        @param value int EntryPoint index
        """
        self.ensure_attached()
        print ep.get_path()
        response = self._send_request(RpcRequest(self.target, 'setValue', ep.get_index(), ep.get_path(), value))
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
        response = self._send_request(RpcRequest(self.target, 'invokeMethod', ep.get_index(), ep.get_path(), method.get_index(), args))
        if response is not None:
            if response.is_success():
                return response.get_response()
        return None
