import RNS
import LXMF
import os
import threading
import time
from ColumbaRNodeInterface import ColumbaRNodeInterface

_global_wrapper_instance = None

class ReticulumWrapper:
    def __init__(self, storage_path):
        global _global_wrapper_instance
        self.storage_path = storage_path
        self.router = None
        self.rnode_interface = None
        _global_wrapper_instance = self
        
        if not os.path.exists(storage_path):
            os.makedirs(storage_path)

        # CRITICAL: Create a minimal config file to prevent RNS from 
        # trying to open restricted sockets or logging to /
        config_path = os.path.join(storage_path, "config")
        if not os.path.exists(config_path):
            with open(config_path, "w") as f:
                f.write("[reticulum]\n")
                f.write("enable_transport = False\n")
                f.write("share_instance = No\n")
                f.write("[logging]\n")
                f.write("loglevel = 4\n")

        # Initialize Reticulum with the explicit safe config
        self.rns_instance = RNS.Reticulum(configdir=storage_path)
        RNS.log(f"RNS Initialized successfully at {storage_path}")

    def set_bridge(self, bridge):
        RNS.log("Connecting RNode Bridge...")
        self.rnode_interface = ColumbaRNodeInterface(
            owner=self.rns_instance,
            name="RNode_433",
            bridge=bridge,
            frequency=433025000,
            sf=8
        )
        RNS.Transport.interfaces.append(self.rnode_interface)
        threading.Thread(target=self.rnode_interface.read_loop, daemon=True).start()

    def start_lxmf(self, user_name):
        # Initialize LXMF Router
        self.router = LXMF.LXMRouter(storagepath=self.storage_path, display_name=user_name)
        self.local_identity = self.router.get_identity()
        return RNS.hexrep(self.local_identity.hash)

    def send_message(self, dest_hash_hex, content, image_bytes=None):
        try:
            dest_hash = RNS.full_hash(bytes.fromhex(dest_hash_hex))
            destination = RNS.Destination(None, RNS.Destination.OUT, RNS.Destination.SINGLE, "lxmf", "delivery")
            destination.hash = dest_hash
            
            # Use LXMF to wrap the message (handles chunking/reliability)
            lxmf_msg = LXMF.LXMessage(
                destination, 
                self.local_identity, 
                content, 
                title="App Transfer", 
                fields={"image": image_bytes} if image_bytes else None
            )
            
            self.router.handle_outbound(lxmf_msg)
            return True
        except Exception as e:
            RNS.log(f"Send error: {e}", RNS.LOG_ERROR)
            return False

    def announce_now(self):
        if self.router:
            self.router.announce()
            return True
        return False

def get_instance(storage_path=None):
    global _global_wrapper_instance
    if _global_wrapper_instance is None:
        _global_wrapper_instance = ReticulumWrapper(storage_path)
    return _global_wrapper_instance
