import socket
import webbrowser
import asyncio
import websockets
import json
from json import JSONDecodeError

# modules for threading
import threading
import time

# user module
##from json_sending_ws import websocket_server_thread

MAX_BUFFER_LEN= 1024
PORT_THIS= 3000
PORT_AS= 3800
IP_ADDR= '127.0.0.1'
MAX_LISTEN=5
SERVER_SOCK_TIMEOUT=0.1
CONN_SOCK_TIMEOUT=0.01
QUIT_MSG='QUIT_MSG'
CHROME_PATH='C:/Program Files (x86)/Google/Chrome/Application/chrome.exe %s'

#some predefined json string and plain string
SHOW_JSON=json.dumps({'type':'show_hide','command':'show'})
HIDE_JSON=json.dumps({'type':'show_hide','command':'hide'})
SHUT_JSON=json.dumps({'type':'shut_window'})
SHUT_SERVER='SHUT_SERVER'

#debug variable
thread_count=0

# class for receiving messages from android server threading
class from_android_server_thread(threading.Thread):
    def __init__(self,name,setASIPAddr=None,ip_addr=IP_ADDR,port_num=PORT_THIS,max_listen=MAX_LISTEN,\
                 max_buffer_len=MAX_BUFFER_LEN,quit_msg=QUIT_MSG,\
                 server_sock_timeout=SERVER_SOCK_TIMEOUT):
        super().__init__()
        self.name = name
        if setASIPAddr is None:
            self.convey_IP_flag=False # ASIP to be conveyed to another thread flag
        else:
            self.convey_IP_flag=True
        self.setASIPAddr=setASIPAddr
        self.convey_queue_sync_to_ws=None
        self.convey_queue_sync_to_as=None
        self.server_socket= None
        self.connection_socket= None
        self.ip_addr= ip_addr
        self.port_num= port_num
        self.max_listen=max_listen
        self.max_buffer_len= max_buffer_len
        self.quit_msg= quit_msg
        #array for storing android socket threads
        self.communicate_threads=[]
        #communication threads deletion
        self.thread_delete_list_lock=threading.Lock()
        self.thread_delete_list=[]
        #stop flag
        self.should_stop=False
        #server socket timeout
        self.server_sock_timeout=server_sock_timeout
    def run(self):
        global SHUT_JSON,SHUT_SERVER

        #debug
        print('FAST started')
        
        # wait until sync queue to websocket server is set
        print('FAST waiting for sync queue is setted...')
        while self.convey_queue_sync_to_ws==None or \
              self.convey_queue_sync_to_as==None:
            time.sleep(0.1)
        print('FAST sync queue is setted!')
        
        # open and bind server listening socket
        self.server_socket= socket.socket(socket.AF_INET,socket.SOCK_STREAM)
        self.server_socket.bind(('',self.port_num))
        self.server_socket.listen(self.max_listen)
        #set socket timeout
        self.server_socket.settimeout(self.server_sock_timeout)

        #debug
        print('brk')

        # handle incomming client connections
        while not self.should_stop:
            # make connection and receive data
            try:
                connection_socket,addr= self.server_socket.accept()
                print(f'from addr:{addr[0]}') #debug
                if self.convey_IP_flag:
                    self.setASIPAddr(addr[0])

                # make communication thread and start
                comm_thread= android_communicate_thread('ACT',connection_socket,self.stop,self.futureDeleteThread,
                                                        self.convey_queue_sync_to_ws, self.convey_queue_sync_to_as)
                self.communicate_threads.append(comm_thread)
                comm_thread.start()
            except socket.timeout as e:
                pass
            except socket.error as e:
                break

            # check if there is any thread exiting(will be deleted)
            while self.thread_delete_list:
                self.thread_delete_list_lock.acquire()
                target_thread=self.thread_delete_list.pop(0)
                self.thread_delete_list_lock.release()
                for i in range(len(self.communicate_threads)):
                    thread=self.communicate_threads[i]
                    if thread==target_thread:
                        self.communicate_threads.pop(i)
                        break

        # before finishing, close all the sockets this server thread has
        self.closeAllSocket()
        # wait created threads
        self.waitThreads()
        
    def stop(self):
        # stop server thread by setting stop flag
        self.should_stop=True
    def closeAllSocket(self):
        # method for close all sockets that this server thread has
        # first, close server listening socket
        try:
            if self.server_socket.fileno()>=0:
                self.server_socket.close()
        except socket.error as e:
            pass
        # second, close server connection socket
        try:
            if self.connection_socket is not None and self.connection_socket.fileno()>=0:
                self.connection_socket.close()
        except socket.error as e:
            pass
    def setSyncQueueToWS(self,sync_queue):
        self.convey_queue_sync_to_ws= sync_queue
    def setSyncQueueToAS(self,sync_queue):
        self.convey_queue_sync_to_as= sync_queue
    def futureDeleteThread(self,thread):
        self.thread_delete_list_lock.acquire()
        self.thread_delete_list.append(thread)
        self.thread_delete_list_lock.release()
    def waitThreads(self):
        for thread in self.communicate_threads:
            thread.stop()
            thread.join()

#class for communicating with android thread
class android_communicate_thread(threading.Thread):
    def __init__(self,name,conn_socket,signalParent,deleteThis,
                 convey_queue_sync_to_ws, convey_queue_sync_to_as,\
                 max_buffer_len=MAX_BUFFER_LEN,quit_msg=QUIT_MSG,\
                 conn_socket_timeout=CONN_SOCK_TIMEOUT):
        super().__init__()
        self.name = name
        self.conn_socket= conn_socket # socket created from listening thread
        self.signalParent= signalParent
        self.deleteThis= deleteThis

        #queues
        self.convey_queue_sync_to_ws=convey_queue_sync_to_ws
        self.convey_queue_sync_to_as=convey_queue_sync_to_as
        
        self.max_buffer_len= max_buffer_len
        self.quit_msg= quit_msg
        self.should_stop= False #stop flag
        #socket timeout
        self.conn_socket_timeout=conn_socket_timeout
        self.conn_socket.settimeout(self.conn_socket_timeout)

    def run(self):
        global SHUT_JSON,SHUT_SERVER
        global thread_count
        this_thread_num=thread_count
        thread_count+=1
        while not self.should_stop:
            #first, check if there is data to be received
            try:
                recv_data=self.conn_socket.recv(self.max_buffer_len)
                recv_data= recv_data.decode('utf-8')

                # check if socket closed
                if not recv_data:
                    print(f'socket broken (detected 0 bytes) while recv msg from phone({this_thread_num})')
                    self.deleteThis(self)
                    break

                is_json=True
                json_object=None
                try:
                    json_object=json.loads(recv_data)
                except JSONDecodeError:
                    is_json=False

                #convey recieved message to browser
                if not is_json or json_object['type']!='disconnect':
                    self.convey_queue_sync_to_ws.put(recv_data)
                    print(recv_data)
                else:
                    json_object['type']='pause'
                    self.convey_queue_sync_to_ws.put(json.dumps(json_object))
                    print(json.dumps(json_object))
                    #delete this thread
                    self.deleteThis(self)
                    break

                #check if recieved message is shut message
                if recv_data==self.quit_msg:
                    self.convey_queue_sync_to_ws.put(SHUT_JSON)
                    self.signalParent()
                    break

##                # check received data is json object or not
##                try:
##                    is_json=True
##                    json_object=json.loads(recv_data)
##                except JSONDecodeError:
##                    is_json=False

                # handle received data properly
##                if is_json:
##                    self.convey_queue_sync_to_ws.put(recv_data)
##                    if 'type' in json_object and json_object['type']=='rtt_measure':
##                        reply_msg=self.convey_queue_sync_to_as.get()
##                        self.connection_socket.sendall(reply_msg.encode('utf-8'))
##                else:
##                    if recv_data==self.quit_msg:
##                        print('client left')
##                        self.convey_queue_sync_to_ws.put(SHUT_JSON)
##                        #self.convey_queue_sync_to_ws.put(SHUT_SERVER)
##                        break
##                    if isYoutubeURL(recv_data):
##                        print('you need to add youtube url handling code...')
##                    else:
##                        print(f'message from client: {recv_data}')
            except socket.timeout as e:
                pass
            except socket.error as e:
                print(f'socket broken while recv msg from phone({this_thread_num})')
                self.deleteThis(self)
                break

            #second, check if there is any pended data to be sent to android
            if not self.convey_queue_sync_to_as.isEmpty():
                as_msg= self.convey_queue_sync_to_as.get()
                print(f'from ws: {as_msg}')
                try:
                    self.conn_socket.sendall(as_msg.encode('utf-8'))
                except socket.error as e:
                    print('socket broken while send msg to phone')
                    self.deleteThis(this)
                    break
            
        # close all sockets
        self.closeAllSocket()

    def stop(self):
        self.should_stop=True
    def closeAllSocket(self):
        # method for close all sockets that this thread has
        try:
            if self.conn_socket.fileno()>=0:
                self.conn_socket.close()
        except socket.error as e:
            pass

# class for sending messages to android server threading
class to_android_server_thread(threading.Thread):
    def __init__(self,name,ip_addr=None,port_num=PORT_AS,max_listen=MAX_LISTEN,\
                 max_buffer_len=MAX_BUFFER_LEN,quit_msg=QUIT_MSG):
        super().__init__()
        self.name = name
        self.convey_queue_sync_to_as=None
        self.connection_socket= None
        self.ip_addr= ip_addr # ip address of android server(phone)
        self.port_num= port_num
        self.max_listen=max_listen
        self.max_buffer_len= max_buffer_len
        self.quit_msg= quit_msg
    def run(self):
        global SHUT_JSON,SHUT_SERVER

        #debug
        print('TAST started')

        # wait until sync queue to AS and ip address of AS are set
        print('TAST waiting for sync queue is set...')
        while self.convey_queue_sync_to_as==None or \
              self.ip_addr==None:
            time.sleep(0.1)
        print('TAST sync queue is setted!')
        
        #debug
        print('brk')

        try:
            while True:
                message= self.convey_queue_sync_to_as.get()

                if message==SHUT_SERVER:
                    print('TAST closing') #debug
                    break
                
                print(message) # debug
                self.connection_socket=socket.socket(socket.AF_INET,socket.SOCK_STREAM)
                print(f'TAST:: connecting to {self.ip_addr}, {self.port_num}')
                print('TAST before connected')
                self.connection_socket.connect((self.ip_addr,self.port_num))
                print('TAST after connected')
                self.connection_socket.send(message.encode('utf-8'))
                self.connection_socket.close()
                self.connection_socket=None
                
        except Exception as e:
            print(e)

        # before finishing, close all the sockets this server thread has
        self.closeAllSocket()
        
    def stop(self):
        global SHUT_SERVER
        self.convey_queue_sync_to_as.put(SHUT_SERVER)
        # stop server thread by closing all the sockets that this thread has
        self.closeAllSocket()
    def closeAllSocket(self):
        # method for close all sockets that this server thread has
        # close connection socket
        try:
            if self.connection_socket is not None and self.connection_socket.fileno()>=0:
                self.connection_socket.close()
        except socket.error as e:
            pass
    def setSyncQueueToAS(self,sync_queue):
        self.convey_queue_sync_to_as= sync_queue
    def setASIPAddr(self,as_ip_addr):
        #self.ip_addr=as_ip_addr
        self.ip_addr='172.30.1.51'
        print('TAST AS ip addr set')

def isYoutubeURL(string):
    if string.find('youtu.be')>=0 or string.find('youtube.com')>=0:
        return True
    else:
        return False

if __name__=='__main__':
    # run websocket server thread
    #get event loop
##    event_loop=asyncio.new_event_loop()
##    asyncio.set_event_loop(event_loop)

    # make asyncio Queue
##    relay_queue= asyncio.Queue()
##
##    ws_st=websocket_server_thread('WSST',event_loop,relay_queue)
##    ws_st.start()
    
    # run android server thread
    fa_st= from_android_server_thread('FAST',None)
    fa_st.start()

    print('is parallel execution possible?')
    print('is it real?')

    try:
        while fa_st.is_alive():
            time.sleep(0.1)
    except KeyboardInterrupt:
        fa_st.stop()
    
    fa_st.join()
##    ws_st.join()
    
##    server_socket= socket.socket(socket.AF_INET,socket.SOCK_STREAM)
##    server_socket.bind(('',PORT))
##    server_socket.listen(MAX_LISTEN)
##
##    try:
##        while True:
##            # make connection and receive data
##            connection_socket,addr= server_socket.accept()
##            #print(f'connection from ip: {addr}')
##            recv_data=connection_socket.recv(MAX_BUFFER_LEN)
##
##            # check received data is json object or not
##            try:
##                is_json=True
##                json_object= json.loads(recv_data)
##            except JSONDecodeError:
##                is_json=False
##                recv_msg=recv_data.decode('utf-8')
##
##            # handle received data properly
##            if is_json:
##                print(json_object)
##            else:
##                if recv_msg==QUIT_MSG:
##                    print('client left')
##                    connection_socket.close()
##                    break
##                if isYoutubeURL(recv_msg):
##                    webbrowser.get(CHROME_PATH).open(recv_msg)
##                else:
##                    print(f'message from client: {recv_msg}')
##
##            # close client connection
##            connection_socket.close()
##    except socket.error as e:
##        print(f'error occurred: {e}')
##
##    server_socket.close()
