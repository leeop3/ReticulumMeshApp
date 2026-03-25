import RNS
import LXMF
import os
import threading
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
            
        self.rns_instance = RNS.Reticulum(configdir=storage_path)
        RNS.log(f"RNS Initialized at {storage_path}")

    def set_bridge(self, bridge):
        RNS.log("Connecting Kotlin Bridge to Python RNodeInterface")
        self.rnode_interface = ColumbaRNodeInterface(
            owner=self.rns_instance,
            name="RNode",
            bridge=bridge,
            frequency=433025000,
            sf=8
        )
        # Register interface with Reticulum Transport layer
        RNS.Transport.interfaces.append(self.rnode_interface)
        
        # Start reading from Bluetooth
        threading.Thread(target=self.rnode_interface.read_loop, daemon=True).start()

    def start_lxmf(self, display_name="Android Node"):
        self.router = LXMF.LXMRouter(storagepath=self.storage_path, display_name=display_name)
        RNS.log("LXMF Router started successfully")

def get_instance(storage_path=None):
    global _global_wrapper_instance
    if _global_wrapper_instance is None:
        _global_wrapper_instance = ReticulumWrapper(storage_path)
    return _global_wrapper_instance
