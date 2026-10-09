#!/usr/bin/env python3
"""Create a private reseller onboarding link. Never post subscription URLs publicly."""
import argparse
from urllib.parse import urlencode

def make_invite(seller: str, subscription: str) -> str:
    if not subscription.startswith("https://"):
        raise ValueError("Only HTTPS subscriptions are supported")
    if not seller.strip() or len(seller) > 80:
        raise ValueError("Seller name must contain 1–80 characters")
    return "noraproxy://setup?" + urlencode({"seller": seller, "url": subscription})

if __name__ == "__main__":
    cli = argparse.ArgumentParser()
    cli.add_argument("--seller", required=True)
    cli.add_argument("--subscription", required=True)
    args = cli.parse_args()
    print(make_invite(args.seller, args.subscription))
    print("CAUTION: This invite contains a secret subscription URL. Share privately.")
