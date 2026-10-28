#!/usr/bin/env python3
"""Simula caminar por la ruta de prueba enviando posiciones al emulador con `adb emu geo fix`.

Usa la misma geometría que app/src/androidTest/.../TestRoute.kt. Va y vuelve por la ruta
indefinidamente, un punto (~10 m) cada 2 segundos, hasta que se lo detenga.
"""
import math
import subprocess
import time

POINTS = 400


def lat(i):
    return -31.3750 + i * 0.00009


def lon(i):
    return -64.6000 + 0.0006 * math.sin(i / 40.0)


def ele(i):
    return 820.0 + 310.0 * math.exp(-(((i - 220.0) / 110.0) ** 2))


def main():
    order = list(range(POINTS)) + list(range(POINTS - 2, 0, -1))
    while True:
        for i in order:
            # geo fix <longitud> <latitud> <altitud> <satélites>
            subprocess.run(
                ["adb", "emu", "geo", "fix", f"{lon(i):.6f}", f"{lat(i):.6f}", f"{ele(i):.1f}", "8"],
                stdout=subprocess.DEVNULL,
                stderr=subprocess.DEVNULL,
                check=False,
            )
            time.sleep(2)


if __name__ == "__main__":
    main()
