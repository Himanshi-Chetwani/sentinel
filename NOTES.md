# Notes

Bad IP lookup uses exact text matching. IPv6 addresses can have multiple equivalent textual
representations, so an address written differently from the value in `bad-ips.json` will not
match. The current IPv4 validator rejects non-canonical dotted-decimal forms such as leading-zero
octets, so this representation issue applies to IPv6.
