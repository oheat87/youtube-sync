import sys
import os

import asyncio
import websockets
import json

# threading modules
import threading
#import queue
import janus

#time checking
import datetime
import time

# to open html page in browser
import webbrowser

#import user module for android server threading
import vs_avd

PORT=3500

SHOW_JSON=json.dumps({'type':'show_hide','command':'show'})
HIDE_JSON=json.dumps({'type':'show_hide','command':'hide'})
SHUT_JSON=json.dumps({'type':'shut_window'})
SHUT_SERVER='SHUT_SERVER'

#json relay queue
JSON_RELAY_QUEUE=None
setSyncQueueToWS=None
setSyncQueueToAS=None

#websocket part execution loop
loop=None
#stop event for websocket server
stop_event=threading.Event()

#class for thread safe queue
class safeQueue:
    def __init__(self):
        self.lock= threading.Lock()
        self.q=[]
    def put(self,item):
        self.lock.acquire()
        self.q.append(item)
        self.lock.release()
    def get(self):
        assert(len(self.q)>0)
        self.lock.acquire()
        ret=self.q.pop(0)
        self.lock.release()
        return ret
    def isEmpty(self):
        return len(self.q)==0
def open_video_page():
    time.sleep(0.5)
    #html_path=os.path.join(os.path.split(os.path.split(os.getcwd())[0])[0],'index.html')
    html_path='index.html'
    webbrowser.open(html_path)
    

def prompt(sync_q):
    global SHOW_JSON,HIDE_JSON,SHUT_JSON
    try:
        while True:
            print('>>>',end='')
            sys.stdout.flush()
            user_input=sys.stdin.readline().rstrip('\n').upper()
            if user_input=='Q':
                sync_q.put(SHUT_JSON)
                sync_q.put(SHUT_SERVER)
                break
            elif user_input=='A':
                sync_q.put(SHOW_JSON)
            elif user_input=='B':
                sync_q.put(HIDE_JSON)
    except KeyboardInterrupt:
        pass

async def json_sender_to_ws(websocket, async_q):
    global SHUT_SERVER
##    global stop_event
##    global loop
    while True:
        json= await async_q.get()
        if json==SHUT_SERVER:
##            loop.close()
##            raise Exception('shut server')
##            stop_event.set()
            break
        await websocket.send(json)

#async def json_sender_to_as(websocket, async_q):
async def json_sender_to_as(websocket, q):
    global SHUT_SERVER
##    global stop_event
##    global loop
    async for message in websocket:           
        #await async_q.put(message)
        q.put(message)
        if message==SHUT_SERVER:
            print('SHUT_SERVER received')
##            stop_event.set()
##            loop.close()
##            raise Exception('shut server')
            break
        elif message=='RTT_REPLY':
            #print('RTT_REPLY received')
            pass

async def server(websocket,path):
    global SHUT_SERVER, setSyncQueueToWS, setSyncQueueToAS
    global stop_event
    queue_to_ws = janus.Queue()
    #queue_to_as = janus.Queue()
    queue_to_as = safeQueue()
    print(f'queue_to_ws==queue_to_as:{queue_to_ws==queue_to_as}')
    print(setSyncQueueToWS)
    setSyncQueueToWS(queue_to_ws.sync_q)
    #setSyncQueueToAS(queue_to_as.sync_q)
    setSyncQueueToAS(queue_to_as)
    print(setSyncQueueToAS)
    loop=asyncio.get_event_loop()
##    finished,unfinished= await asyncio.wait( \
##        [ \
##            json_sender(websocket,queue.async_q), \
##            loop.run_in_executor(None,lambda sync_q: prompt(sync_q), queue.sync_q)\
##            ], \
##        return_when=asyncio.FIRST_COMPLETED)

##    await asyncio.gather( \
##        json_sender(websocket,queue.async_q), \
##        loop.run_in_executor(None,ast.start), \
##        loop.run_in_executor(None,lambda queue: prompt(queue),queue.sync_q) \
##        )
    
##    await json_sender_to_ws(websocket,queue_to_ws.async_q)
##    while True:
    sender_to_ws_task = asyncio.ensure_future(json_sender_to_ws(websocket,queue_to_ws.async_q))
    #sender_to_as_task = asyncio.ensure_future(json_sender_to_as(websocket,queue_to_as.async_q))
    sender_to_as_task = asyncio.ensure_future(json_sender_to_as(websocket,queue_to_as))
    done,pending= await asyncio.wait( \
        [sender_to_ws_task,sender_to_as_task], \
        return_when=asyncio.FIRST_COMPLETED \
        )
    for task in pending:
        task.cancel()
##    raise Exception('shut server')
    stop_event.set()
    

##    await queue.async_q.put(SHUT_SERVER)

##    for task in unfinished:
##        task.cancel()
##        try:
##            await task
##        except asyncio.CancelledError:
##            print('asyncio.CancelledError')
##
##    await asyncio.sleep(5)
##    loop.stop()
    #prompt user to indeed close the websocket json relay server to browser
##    print('press Ctrl+C to indeed close the program')
##    sys.stdout.flush()
async def websocket_server(loop, stop_event):
    async with websockets.serve(server, 'localhost', 3500):
        await loop.run_in_executor(None, stop_event.wait)

if __name__=='__main__':
    # set timer to open video page in browser with another thread
    open_page_thread= threading.Thread(target=open_video_page)
    open_page_thread.start()

##    # run 'to android server thread'
##    tast= vs_avd.to_android_server_thread('TAST')
##    setSyncQueueToAS=tast.setSyncQueueToAS #get ast thread queue to android server setter method
##    tast.start()

    # run 'from android server thread'
    fast= vs_avd.from_android_server_thread('FAST')
    setSyncQueueToWS=fast.setSyncQueueToWS #get ast thread queue to websocket server setter method
    setSyncQueueToAS=fast.setSyncQueueToAS #get ast thread queue to android server setter method
    fast.start()

    # websocket server part
##    start_server = websockets.serve(server, 'localhost', 3500)
##    print(f'start_server: {start_server}')
##    server_task= asyncio.ensure_future(start_server)
    
    loop=asyncio.get_event_loop()
##    loop.run_until_complete(start_server)
    loop.run_until_complete(websocket_server(loop,stop_event))
##    try:
##        loop.run_forever()
##    finally:
##        loop.close()

    #join video page open thread
    open_page_thread.join()
    fast.join()
##    tast.join()
