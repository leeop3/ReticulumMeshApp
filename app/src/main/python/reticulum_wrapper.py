import RNS
import LXMF
import os
import time
import threading
from ColumbaRNodeInterface import ColumbaRNodeInterface
from rmsp_client import get_rmsp_client
from rns_api import RnsApi

_global_wrapper_instance = None

class ReticulumWrapper:
    def __init__(self, storage_path, config_dict):
        global _global_wrapper_instance
        self.storage_path = storage_path
        self.router = None
        self.identities = {}
        self.kotlin_rnode_bridge = None # Set from Kotlin
        self.rns_api = RnsApi()
        _global_wrapper_instance = self
        
        # Initialize RNS
        if not os.path.exists(storage_path):
            os.makedirs(storage_path)
            
        self.rns_instance = RNS.Reticulum(configdir=storage_path)
        RNS.log(f"RNS Initialized at {storage_path}")

    def set_bridge(self, bridge):
        self.kotlin_rnode_bridge = bridge

    def start_lxmf(self, display_name="Android Node"):
        self.router = LXMF.LXMRouter(storagepath=self.storage_path, display_name=display_name)
        get_rmsp_client().initialize()
        RNS.log("LXMF Router and RMSP Client started")

    def get_api(self):
        return self.rns_api

def get_instance(storage_path=None, config=None):
    global _global_wrapper_instance
    if _global_wrapper_instance is None:
        _global_wrapper_instance = ReticulumWrapper(storage_path, config)
    return _global_wrapper_instance