# Security

CamGrid stores camera stream URLs, which often contain user names and passwords, and on a TV it can open a small transfer page on the local network. If you find a way to read those credentials, bypass the transfer page's PIN, or make the app talk to an address the user did not configure, please report it privately.

## Reporting a vulnerability

Use [Report a vulnerability](https://github.com/Vando-sketch/CamGrid/security/advisories/new) on the repository's Security tab. Please do not open a public issue for it.

Include the platform and CamGrid version, what an attacker needs (same Wi-Fi, a crafted backup file, a malicious go2rtc server, physical access) and the steps to reproduce. Leave out real addresses and credentials.

CamGrid is maintained in spare time. Expect a first answer within about a week. Fixes go into the next preview build from `main`; there are no separately supported older versions.

## Scope

In scope: the CamGrid apps and their code in this repository, including config encryption, backup encryption, the TV transfer page, URL redaction in logs, and the CI workflows.

Out of scope: go2rtc, your cameras, and the security of your own network. CamGrid is meant for use on a trusted local network. It uses plain HTTP and RTSP when you configure plain URLs, because that is what go2rtc offers on a LAN by default.

## What CamGrid does to protect your data

See [Privacy and security](README.md#privacy-and-security) in the README.
