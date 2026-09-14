"""Generate a local QR image containing a raw Claude API key."""

from getpass import getpass
from pathlib import Path

import qrcode


def main() -> None:
    key = getpass("Paste your Claude API key (input is hidden): ").strip()
    if not key.startswith("sk-ant-") or len(key) <= 20:
        raise SystemExit("That does not look like a Claude API key.")

    output = Path(__file__).with_name("remindme-api-key.png")
    image = qrcode.make(key)
    image.save(output)
    print(f"QR code saved to {output}")
    print("Delete the PNG after scanning it into RemindMe.")


if __name__ == "__main__":
    main()
