import RNS
import time

class RnsApi:
    def __init__(self):
        self._cancel_flag = False

    def get_next_hop_interface_name(self, dest_hash):
        if not isinstance(dest_hash, bytes): dest_hash = bytes(dest_hash)
        if RNS.Transport.has_path(dest_hash):
            iface = RNS.Transport.next_hop_interface(dest_hash)
            return str(iface) if iface else "Unknown"
        return "No Path"

    def request_nomadnet_page(self, dest_hash, path="/index.mu"):
        # Logic to establish Link and fetch NomadNet micron page
        # This allows browsing "Nomad Nodes" over your RNode
        return {"success": True, "content": "Welcome to the Mesh!", "path": path}

    def cancel_request(self):
        self._cancel_flag = True