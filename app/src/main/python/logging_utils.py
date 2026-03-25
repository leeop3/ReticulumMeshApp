import RNS

def log_info(module, func, msg):
    RNS.log(f"[{module}.{func}] {msg}", RNS.LOG_INFO)

def log_error(module, func, msg):
    RNS.log(f"[{module}.{func}] {msg}", RNS.LOG_ERROR)