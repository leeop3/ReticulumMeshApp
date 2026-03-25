import RNS
import time

class KISS:
    FEND = 0xC0
    CMD_DATA = 0x00
    CMD_FREQUENCY = 0x01
    CMD_SF = 0x04
    
    @staticmethod
    def escape(data):
        return data.replace(bytes([0xDB]), bytes([0xDB, 0xDD])).replace(bytes([0xC0]), bytes([0xDB, 0xDC]))

class ColumbaRNodeInterface(RNS.Interfaces.Interface.Interface):
    def __init__(self, owner, name, bridge, frequency=433025000, sf=8):
        super().__init__()
        self.owner = owner
        self.name = name
        self.bridge = bridge # The Kotlin Bluetooth Bridge
        self.online = True
        
        # Provision Radio
        self.set_frequency(frequency)
        self.set_sf(sf)

    def set_frequency(self, freq):
        data = bytes([KISS.FEND, KISS.CMD_FREQUENCY]) + freq.to_bytes(4, 'big') + bytes([KISS.FEND])
        self.bridge.writeSync(data)

    def set_sf(self, sf):
        data = bytes([KISS.FEND, KISS.CMD_SF, sf, KISS.FEND])
        self.bridge.writeSync(data)

    def process_outgoing(self, data):
        if self.online:
            kiss_frame = bytes([KISS.FEND, KISS.CMD_DATA]) + KISS.escape(data) + bytes([KISS.FEND])
            self.bridge.writeSync(kiss_frame)

    def read_loop(self):
        # Called by a background thread to poll the Bluetooth socket
        while self.online:
            raw = self.bridge.read()
            if raw:
                RNS.Transport.inbound(bytes(raw), self)
            time.sleep(0.01)