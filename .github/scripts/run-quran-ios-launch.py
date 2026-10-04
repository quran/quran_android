"""Launch the built app: compiling a framework alone does not detect startup crashes."""
import argparse
import json
import plistlib
import re
import time
import subprocess
from pathlib import Path


def simctl(*args, **kwargs):
    return subprocess.run(["xcrun", "simctl", *args], check=True, **kwargs)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("app", type=Path)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    info = plistlib.loads((args.app / "Info.plist").read_bytes())
    if info.get("CADisableMinimumFrameDurationOnPhone") is not True:
        raise RuntimeError("Built app lacks Compose's mandatory frame-duration setting")
    bundle_id = info["CFBundleIdentifier"]
    devices = json.loads(simctl("list", "devices", "available", "--json", capture_output=True, text=True).stdout)
    iphones = [d for runtime, entries in devices["devices"].items()
               if "iOS" in runtime for d in entries if d["name"].startswith("iPhone")]
    if not iphones:
        raise RuntimeError("No available iPhone simulator")
    device = next((d for d in iphones if d["state"] == "Booted"), iphones[0])
    udid = device["udid"]
    if device["state"] != "Booted":
        simctl("boot", udid, timeout=120)
    simctl("bootstatus", udid, "-b", timeout=180)
    simctl("install", udid, str(args.app.resolve()), timeout=60)
    args.output.mkdir(parents=True, exist_ok=True)
    with (args.output / "launch.log").open("w") as log:
        app = subprocess.Popen(["xcrun", "simctl", "launch", "--console", "--terminate-running-process", udid, bundle_id], stdout=log, stderr=subprocess.STDOUT)
        try:
            launch_pattern = re.compile(rf"(?m)^{re.escape(bundle_id)}: ([1-9][0-9]*)$")
            deadline = time.monotonic() + 30
            while not launch_pattern.search((args.output / "launch.log").read_text()):
                if app.poll() is not None or time.monotonic() >= deadline:
                    raise RuntimeError("No bundle/PID launch confirmation; see launch.log")
                time.sleep(0.25)
            try:
                app.wait(timeout=20)
            except subprocess.TimeoutExpired:
                # --console blocks for the application lifetime; early exit is a failure.
                simctl("io", udid, "screenshot", str(args.output / "launch.png"), timeout=30)
                if app.poll() is not None:
                    raise RuntimeError("Application exited while capturing its first screen")
                print(f"PASS: {bundle_id} stayed running for 20 seconds on {device['name']}")
            else:
                raise RuntimeError(f"Application exited during launch (status {app.returncode}); see launch.log")
        finally:
            subprocess.run(["xcrun", "simctl", "terminate", udid, bundle_id], check=False, capture_output=True, timeout=30)
            try:
                app.wait(timeout=10)
            except subprocess.TimeoutExpired:
                app.kill()
                app.wait()


if __name__ == "__main__":
    main()
