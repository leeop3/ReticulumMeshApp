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
        self.rns_instance = None # Initialize to None to prevent AttributeError
        _global_wrapper_instance = self
        
        if not os.path.exists(storage_path):
            os.makedirs(storage_path)

        # CRITICAL: Hard-disable AutoInterface and Sockets
        config_path = os.path.join(storage_path, "config")
        with open(config_path, "w") as f:
            f.write("[reticulum]\n")
            f.write("enable_transport = False\n")
            f.write("share_instance = No\n")
            f.write("is_gateway = No\n")
            f.write("[logging]\n")
            f.write("loglevel = 4\n")
            f.write("[interfaces]\n")
            f.write("  [[TCP Server Interface]]\n")
            f.write("    type = TCPInterface\n")
            f.write("    enabled = No\n")
            f.write("  [[UDP Interface]]\n")
            f.write("    type = UDPInterface\n")
            f.write("    enabled = No\n")

        try:
            # Initialize Reticulum
            self.rns_instance = RNS.Reticulum(configdir=storage_path)
            RNS.log(f"RNS Core started at {storage_path}")
        except Exception as e:
            RNS.log(f"RNS Startup Error: {e}", RNS.LOG_ERROR)

    def set_bridge(self, bridge):
        if self.rns_instance is None:
            RNS.log("Cannot set bridge: RNS instance not running", RNS.LOG_ERROR)
            return
            
        RNS.log("Connecting RNode via Kotlin Bridge...")
        self.rnode_interface = ColumbaRNodeInterface(
            owner=self.rns_instance,
            name="RNode_BT",
            bridge=bridge,
            frequency=433025000,
            sf=8
        )
        # Manually attach to transport
        RNS.Transport.interfaces.append(self.rnode_interface)
        threading.Thread(target=self.rnode_interface.read_loop, daemon=True).start()

    def start_lxmf(self, user_name):
        if self.rns_instance is None: return "ERROR"
        self.router = LXMF.LXMRouter(storagepath=self.storage_path, display_name=user_name)
        self.local_identity = self.router.get_identity()
        return RNS.hexrep(self.local_identity.hash)

    def send_message(self, dest_hash_hex, content, image_bytes=None):
        if not self.router: return False
        try:
            # Clean hex string and convert to bytes
            clean_hex = dest_hash_hex.replace("<", "").replace(">", "").replace(" ", "")
            dest_hash = bytes.fromhex(clean_hex)
            
            # Create a 'Single' destination
            destination = RNS.Destination(None, RNS.Destination.OUT, RNS.Destination.SINGLE, "lxmf", "delivery")
            destination.hash = dest_hash
            
            lxmf_msg = LXMF.LXMessage(
                destination, 
                self.local_identity, 
                content, 
                title="RNS Mesh Transfer", 
                fields={"image": image_bytes} if image_bytes else None
            )
            self.router.handle_outbound(lxmf_msg)
            return True
        except Exception as e:
            print(f"Error sending: {e}")
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
