# detect_dpad.py
# Usage: python detect_dpad.py
import time
import pygame

pygame.init()
pygame.joystick.init()

if pygame.joystick.get_count() == 0:
    print("No joystick found. Plug in controller and re-run.")
    raise SystemExit(1)

js = pygame.joystick.Joystick(0)
js.init()
print(f"Joystick 0: name='{js.get_name()}' axes={js.get_numaxes()} buttons={js.get_numbuttons()} hats={js.get_numhats()}")

def dump_state():
    axes = {i: round(js.get_axis(i), 3) for i in range(js.get_numaxes())}
    buttons = {i: js.get_button(i) for i in range(js.get_numbuttons())}
    hats = {i: js.get_hat(i) for i in range(js.get_numhats())}
    print(f"axes={axes}  buttons={buttons}  hats={hats}")

print("Polling at 60Hz. Press D-Pad directions now. Ctrl-C to quit.")
try:
    while True:
        pygame.event.pump()
        dump_state()
        time.sleep(1/60)
except KeyboardInterrupt:
    print("Stopped.")
finally:
    js.quit()
    pygame.quit()
